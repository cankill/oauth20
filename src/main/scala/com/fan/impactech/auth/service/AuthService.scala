package com.fan.impactech.auth.service

import java.net.URL
import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef => TypedActorRef}
import akka.http.scaladsl.server.directives.Credentials
import com.fan.impactech.auth.dao.AuthDao
import com.fan.impactech.auth.domain.StoredCredentials
import com.typesafe.config.Config
import io.lemonlabs.uri.Url
import io.lemonlabs.uri.typesafe.{QueryKeyValue, TraversableParams}

import scala.concurrent.duration.SECONDS
import scala.util.{Failure, Success}
import scala.concurrent.duration._


object AuthService {
  def name = "authService"

  case class AuthorizationRequest(responseType: String,
                                  clientId: String,
                                  redirectUrl: Option[String],
                                  scope: Option[String],
                                  state: Option[String])

  sealed trait Protocol
  sealed trait AuthResponseMessage

  final case class Authenticate(id: String, credentials: Credentials.Provided, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol
  final case object Authenticated extends AuthResponseMessage

  final case class Authorize(userName: String, authReq: AuthorizationRequest, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol
  final case class Authorized(redirectUrl: String) extends AuthResponseMessage
  final case class Unauthorized(redirectUrl: String) extends AuthResponseMessage

  final case class Rejected(error: String, reason: String) extends AuthResponseMessage
  final case class DbFailure(ex: Throwable) extends AuthResponseMessage

  private final case class CheckCodeExists (userName: String, authReq: AuthorizationRequest, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol
  private final case class GenerateCode (userName: String, authReq: AuthorizationRequest, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol

  private final case class WrappedAuthResult(result: AuthResponseMessage, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol
  private final case class WrappedAuthResultWithLockKey(result: AuthResponseMessage, lockKey: LockKey, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol

  private case class State(locked: Set[LockKey])
  private object State {
    def empty: State = State(Set.empty)
  }

  def behaviour (config: Config, authDao: AuthDao): Behavior[Protocol] = {
    Behaviors.setup[Protocol] { implicit context ⇒
      val log = context.log // thread safe copy
      val expiration: FiniteDuration = config.getDuration("oauth20.auth_code.expiration", SECONDS) seconds

      def readyState (state: State): Behavior[Protocol] = Behaviors.receiveMessage {
        case Authenticate(userName, credentials, sender) =>
          log.debug(s"""Received message: Authenticate($userName, $credentials, $sender)""")

          context.pipeToSelf(authDao.getUserCredentials(userName)) {
            case Success(Some(StoredCredentials(_, passwd))) if credentials.verify(passwd) =>
              WrappedAuthResult(Authenticated, sender)

            case Success(_) =>
              WrappedAuthResult(Rejected("access_denied", "Authentication failed"), sender)

            case Failure(ex) => WrappedAuthResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case Authorize(userName, authRequest, sender) =>
          log.debug(s"""Received message: Authorize($userName, $authRequest, $sender)""")

          context.pipeToSelf(authDao.getClientCallbackUrl(authRequest.clientId)) {
            case Success(Some(callbackUrl)) if isValidCallbackUrl(authRequest.redirectUrl, callbackUrl) =>
              if (!isResponseTypeSupported(authRequest)) {
                val callBackUrlReply = makeReplyUrl(callbackUrl,
                                                    authRequest.state,
                                                    "error" -> "unsupported_response_type",
                                                    "error_description" -> s"${authRequest.responseType} is unsupported")
                WrappedAuthResult(Unauthorized(callBackUrlReply), sender)
              } else {
                CheckCodeExists(userName, authRequest, sender)
              }

            case Success(None) => WrappedAuthResult(Rejected("access_denied", s"Client ${authRequest.clientId} not found"), sender)

            case Success(_) => WrappedAuthResult(Rejected("invalid_request", "Wrong redirect_uri provided"), sender)

            case Failure(ex) => WrappedAuthResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case CheckCodeExists(userName, authRequest, sender) =>
          log.debug(s"""Received message: CheckCodeExists($userName, $authRequest, $sender)""")
          context.pipeToSelf(authDao.getAuthorizationCode(authRequest.clientId, userName, authRequest.redirectUrl.get)) {
            case Success(Some(existAuthCodeDTO)) =>
                val callBackUrlReply = makeReplyUrl(authRequest.redirectUrl.get,
                                                    authRequest.state,
                                                    "error" -> "temporarily_unavailable",
                                                    "error_description" -> s"Active code exists")
                WrappedAuthResult(Unauthorized(callBackUrlReply), sender)

            case Success(None) =>
              GenerateCode(userName, authRequest, sender)

            case Failure(ex) => WrappedAuthResult(DbFailure(ex), sender)
          }

          Behaviors.same

        case GenerateCode(userName, authRequest, sender) if state.locked.contains(makeLockKey(userName, authRequest)) =>
          log.debug(s"""Received message: GenerateCode($userName, $authRequest, $sender) and state is locked""")
          val callBackUrlReply = makeReplyUrl(authRequest.redirectUrl.get,
                                              authRequest.state,
                                              "error" -> "temporarily_unavailable",
                                              "error_description" -> s"Concurrent requests are not supported")

          sender ! Unauthorized(callBackUrlReply)

          Behaviors.same

        case GenerateCode(userName, authRequest, sender) =>
          log.debug(s"""Received message: GenerateCode($userName, $authRequest, $sender)""")
          val lockKey = makeLockKey(userName, authRequest)
          val code = UUID.randomUUID().toString
          context.pipeToSelf(authDao.makeAuthorizationCode(authRequest.clientId, userName, authRequest.redirectUrl.get, code, expiration)) {
            case Success(false) =>
              val callBackUrlReply = makeReplyUrl(authRequest.redirectUrl.get,
                                                  authRequest.state,
                                                  "error" -> "server_error",
                                                  "error_description" -> s"DB error, check logs")
              WrappedAuthResultWithLockKey(Unauthorized(callBackUrlReply), lockKey, sender)

            case Success(true) =>
              val callBackUrlReply = makeReplyUrl(authRequest.redirectUrl.get,
                                                  authRequest.state,
                                                  "code" -> code)
              WrappedAuthResultWithLockKey(Authorized(callBackUrlReply), lockKey, sender)

            case Failure(ex) => WrappedAuthResultWithLockKey(DbFailure(ex), lockKey, sender)
          }

          readyState(state.copy(locked = state.locked + lockKey))

        case WrappedAuthResultWithLockKey(result, lockKey, replyTo) =>
          replyTo ! result

          readyState(state.copy(locked = state.locked - lockKey))

        case WrappedAuthResult(result, replyTo) =>
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

  private def isResponseTypeSupported (authRequest: AuthorizationRequest): Boolean = authRequest.responseType match {
    case "code" => true
    case _ => false
  }

  private def makeReplyUrl[KV: QueryKeyValue](callBackUrl: String, state: Option[String], params: KV*): String = {
    val parsedCallbackUrl = Url.parse(callBackUrl)
    val callbackUrlWithState = if (state.isDefined) parsedCallbackUrl.addParam("state" -> state.get) else parsedCallbackUrl
    callbackUrlWithState.addParams(params).toStringRaw
  }

  final case class LockKey(userName: String, clientId: String, callbackUrl: String)
  def makeLockKey (userName: String, authRequest: AuthorizationRequest): LockKey = {
    LockKey(userName, authRequest.clientId, authRequest.redirectUrl.get)
  }
}
