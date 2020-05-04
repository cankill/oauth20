package com.fan.impactech.token.service

import java.net.URL
import java.time.Instant
import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef => TypedActorRef}
import akka.http.scaladsl.server.directives.Credentials
import com.fan.impactech.auth.dao.AuthDao
import com.fan.impactech.auth.domain.StoredCredentials
import com.fan.impactech.auth.service.AuthService.AuthorizationRequest
import com.fan.impactech.token.dao.TokenDao
import com.fan.impactech.token.dao.domain.TokenDTO
import com.typesafe.config.Config
import io.lemonlabs.uri.Url
import io.lemonlabs.uri.typesafe.{QueryKeyValue, TraversableParams}

import scala.concurrent.duration.SECONDS
import scala.util.{Failure, Success}
import scala.concurrent.duration._


object TokenService {
  def name = "tokenService"

  case class TokenRequest(grantType: String,
                          code: String,
                          redirectUrl: Option[String])

  case class TokenRefreshRequest(grantType: String,
                                 refresh_token: String)

  sealed trait Protocol
  sealed trait TokenResponseMessage

  final case class Oauth20Authenticate(access_token: String, credentials: Credentials.Provided, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol

  final case class Authenticate(clientId: String, credentials: Credentials.Provided, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol
  final case object Authenticated extends TokenResponseMessage

  final case class GetToken(clientId: String, tokenReq: TokenRequest, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol
  final case class RefreshToken(clientId: String, refreshToken: String, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol
  final case class TokenResponse(token: TokenDTO) extends TokenResponseMessage

  final case class Rejected(error: String, reason: String) extends TokenResponseMessage
  final case class DbFailure(ex: Throwable) extends TokenResponseMessage

  private final case class CheckTokenExists (clientId: String, userName: String, code: String, tokenReq: TokenRequest, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol
  private final case class GenerateToken (clientId: String, userName: String, code: String, tokenReq: TokenRequest, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol
  private final case class RegenerateToken (refreshToken: String, clientId: String, existsToken: TokenDTO, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol

  private final case class WrappedTokenResult(result: TokenResponseMessage, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol
  private final case class WrappedTokenResultWithLockKey(result: TokenResponseMessage, lockKey: LockKey, replyTo: TypedActorRef[TokenResponseMessage]) extends Protocol

  private case class State(locked: Set[LockKey])
  private object State {
    def empty: State = State(Set.empty)
  }

  def behaviour (config: Config, tokenDao: TokenDao): Behavior[Protocol] = {
    Behaviors.setup[Protocol] { implicit context ⇒
      val log = context.log // thread safe copy
      val expiration: FiniteDuration = config.getDuration("oauth20.token.expiration", SECONDS) seconds

      def readyState (state: State): Behavior[Protocol] = Behaviors.receiveMessage {
        case Oauth20Authenticate(access_token, credentials, sender) =>
          log.debug(s"""Received message: Oauth20Authenticate($access_token, $credentials, $sender)""")

          context.pipeToSelf(tokenDao.getValidToken(access_token)) {
            case Success(Some(existTokenDTO)) if credentials.verify(existTokenDTO.accessToken) =>
              WrappedTokenResult(Authenticated, sender)

            case Success(_) =>
              WrappedTokenResult(Rejected("access_denied", "Authentication failed"), sender)

            case Failure(ex) => WrappedTokenResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case Authenticate(clientId, credentials, sender) =>
          log.debug(s"""Received message: Authenticate($clientId, $credentials, $sender)""")

          context.pipeToSelf(tokenDao.getClientCredentials(clientId)) {
            case Success(Some(StoredCredentials(_, passwd))) if credentials.verify(passwd) =>
              WrappedTokenResult(Authenticated, sender)

            case Success(_) =>
              WrappedTokenResult(Rejected("access_denied", "Authentication failed"), sender)

            case Failure(ex) => WrappedTokenResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case GetToken(clientId, tokenRequest, sender) =>
          log.debug(s"""Received message: GetToken($clientId, $tokenRequest, $sender)""")

          context.pipeToSelf(tokenDao.getAuthCode(clientId, tokenRequest.code)) {
            case Success(Some(existAuthCode)) if isValidCallbackUrl(tokenRequest.redirectUrl, existAuthCode.callbackUrl) =>
              // TODO: add support for other grant_types
              if (!isGrantTypeSupported(tokenRequest)) {
                WrappedTokenResult(Rejected("invalid_request", s"grant_type: '${tokenRequest.grantType}' is unsupported"), sender)
              } else {
                CheckTokenExists(clientId, existAuthCode.userName, existAuthCode.code, tokenRequest.copy(redirectUrl = Some(existAuthCode.callbackUrl)), sender)
              }

            case Success(None) => WrappedTokenResult(Rejected("access_denied", s"Authorization code not found"), sender)

            case Success(_) => WrappedTokenResult(Rejected("invalid_request", "Wrong redirect_uri provided"), sender)

            case Failure(ex) => WrappedTokenResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case CheckTokenExists(clientId, userName, code, tokenRequest, sender) =>
          log.debug(s"""Received message: CheckTokenExists($clientId, $tokenRequest, $sender)""")
          context.pipeToSelf(tokenDao.getToken(clientId, userName, tokenRequest.redirectUrl.get)) {
            case Success(Some(existTokenDTO)) =>
                WrappedTokenResult(TokenResponse(existTokenDTO), sender)

            case Success(None) => GenerateToken(clientId, userName, code, tokenRequest, sender)

            case Failure(ex) => WrappedTokenResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case GenerateToken(clientId, userName, code, tokenRequest, sender) if state.locked.contains(makeLockKey(clientId, userName, tokenRequest)) =>
          log.debug(s"""Received message: GenerateToken($clientId, $userName, $code, $tokenRequest, $sender) and state is locked""")
          sender ! Rejected("temporarily_unavailable", "Concurrent requests are not supported")

          Behaviors.same

        case GenerateToken(clientId, userName, code, tokenRequest, sender) =>
          log.debug(s"""Received message: GenerateToken($clientId, $userName, $code, $tokenRequest, $sender)""")
          val lockKey = makeLockKey(clientId, userName, tokenRequest)
          val accessToken = UUID.randomUUID().toString
          val refreshToken = UUID.randomUUID().toString
          context.pipeToSelf(tokenDao.makeToken(clientId, userName, tokenRequest.redirectUrl.get, code, accessToken, refreshToken, expiration)) {
            case Success(tokenDTO) => WrappedTokenResultWithLockKey(TokenResponse(tokenDTO), lockKey, sender)
            case Failure(ex) => WrappedTokenResultWithLockKey(DbFailure(ex), lockKey, sender)
          }

          readyState(state.copy(locked = state.locked + lockKey))

        case RefreshToken(clientId, refreshToken, sender) =>
          log.debug(s"""Received message: RefreshToken($clientId, $refreshToken, $sender)""")
          context.pipeToSelf(tokenDao.getToken(refreshToken)) {
            case Success(Some(existsTokenDTO)) =>
              RegenerateToken(refreshToken, clientId, existsTokenDTO, sender)

            case Success(None) => WrappedTokenResult(Rejected("access_denied", s"refresh_token: $refreshToken not found"), sender)

            case Failure(ex) => WrappedTokenResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case RegenerateToken(refreshToken, clientId, existTokenDTO, sender) if state.locked.contains(makeLockKey(refreshToken)) =>
          log.debug(s"""Received message: RegenerateToken($refreshToken, $clientId, $existTokenDTO, $sender) and state is locked""")
          sender ! Rejected("temporarily_unavailable", "Concurrent requests are not supported")

          Behaviors.same

        case RegenerateToken(refreshToken, clientId, existTokenDTO, sender) =>
          log.debug(s"""Received message: RegenerateToken($refreshToken, $clientId, $existTokenDTO, $sender)""")
          val lockKey = makeLockKey(refreshToken)
          val newAccessToken = UUID.randomUUID().toString
          val newRefreshToken = UUID.randomUUID().toString
          context.pipeToSelf(tokenDao.regenerateToken(refreshToken, clientId, existTokenDTO.userName, existTokenDTO.callbackUrl, newAccessToken, newRefreshToken, expiration)) {
            case Success(tokenDTO) => WrappedTokenResultWithLockKey(TokenResponse(tokenDTO), lockKey, sender)
            case Failure(ex) => WrappedTokenResultWithLockKey(DbFailure(ex), lockKey, sender)
          }

          readyState(state.copy(locked = state.locked + lockKey))

        case WrappedTokenResultWithLockKey(result, lockKey, replyTo) =>
          replyTo ! result

          readyState(state.copy(locked = state.locked - lockKey))

        case WrappedTokenResult(result, replyTo) =>
          replyTo ! result
          Behaviors.same

        case msg =>
          log.debug(s"""Ignore received message in a ready State: $msg""")
          Behaviors.same
      }

      readyState(State.empty)
    }
  }
  
  def isValidCallbackUrl (redirectUrlOpt: Option[String], callBackUrl: String): Boolean =
    redirectUrlOpt.forall(_ == callBackUrl)

  private def isGrantTypeSupported (tokenRequest: TokenRequest): Boolean = tokenRequest.grantType match {
    case "authorization_code" => true
    case _ => false
  }

  sealed trait LockKey
  final case class LockKeyA(clientId: String, userName: String, callbackUrl: String) extends LockKey
  final case class LockKeyB(refreshToken: String) extends LockKey
  def makeLockKey (clientId: String, userName: String, tokenRequest: TokenRequest): LockKey = {
    LockKeyA(clientId, userName, tokenRequest.redirectUrl.get)
  }

  def makeLockKey (refreshToken: String): LockKey = {
    LockKeyB(refreshToken)
  }
}
