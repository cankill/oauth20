package com.fan.impactech.token.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.fan.impactech.client.http.validate.ClientValidator
import com.fan.impactech.client.repo.ClientRepository
import com.fan.impactech.client.repo.ClientRepository.{ClientDeleted, ClientFound, ClientNotFound, ClientResponseMessage, GetClient}
import com.fan.impactech.token.mapper.TokenMapper
import com.fan.impactech.token.service.TokenService
import com.fan.impactech.token.service.TokenService.{Authenticate, Authenticated, DbFailure, GenerateToken, GetToken, RefreshToken, Rejected, TokenResponseMessage}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.syntax._
import io.circe.generic.auto._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TokenRestService (implicit inject: Injector) extends Injectable with FailFastCirceSupport with LazyLogging {
  private implicit val executionContext: ExecutionContext = inject[ExecutionContext]('executionContext)
  private implicit val actorSystem: ActorSystem[Nothing] = inject[ActorSystem[Nothing]]

  val tokenService: TypedActorRef[TokenService.Protocol] = inject[TypedActorRef[TokenService.Protocol]]

  private val config: Config = inject[Config]
  private implicit val timeout: Timeout = 2.minutes

  def myClientPassAuthenticator(credentials: Credentials): Future[Option[String]] = credentials match {
      case p @ Credentials.Provided(id) =>
        logger.debug(s"Got request to authenticate: $id")
        tokenService.ask[TokenResponseMessage](ref => Authenticate(id, p, ref)).transform {
          case Success(Authenticated) =>
            logger.error(s"Authenticated")
            Success(Some(id))

          case Success(Rejected(error, reason)) =>
            logger.error(s"Rejected with code: $error and reason: $reason")
            Success(Option.empty[String])

          case Success(DbFailure(ex)) =>
            logger.error(s"Rejected. Db failure: $ex")
            Success(Option.empty[String])

          case Failure(ex) =>
            logger.error(s"Failed to Authenticate with exception: $ex")
            Success(Option.empty[String])
        }

      case _ => Future.successful(None)
  }

  def tokenRoute: Route =
    post {
        path("oauth20" / "token") {
          authenticateBasicAsync(realm = "oauth20", myClientPassAuthenticator) { clientId =>
            parameters('grant_type, 'code, 'redirect_uri.?).as(TokenService.TokenRequest) { tokenRequest =>
              logger.debug(s"Got request to generate a token: $tokenRequest for a client: $clientId")
              onComplete(tokenService.ask[TokenResponseMessage](ref => GetToken(clientId, tokenRequest, ref))) {
                case Success(TokenService.TokenResponse(token)) =>
                  complete(StatusCodes.OK, TokenMapper.toRepresentation(token).asJson)

                case Success(TokenService.Rejected(error, reason)) =>
                  logger.error(s"Rejected with code: $error and reason: $reason")
                  complete(StatusCodes.BadRequest -> reason)

                case Success(TokenService.DbFailure(ex)) =>
                  logger.error("Exception: ", ex)
                  complete(StatusCodes.InternalServerError -> "DataBase problems")

                case Failure(e) =>
                  logger.error("Exception: ", e)
                  complete(StatusCodes.InternalServerError -> e.getMessage)
              }
            }
          }
      }
  } ~
  post {
    path("oauth20" / "token") {
      authenticateBasicAsync(realm = "oauth20", myClientPassAuthenticator) { clientId =>
        parameters('grant_type, 'refresh_token) { (grantType, refreshToken) =>
          logger.debug(s"Got request with grant_type: $grantType and refresh token: $refreshToken for a client: $clientId")
          if (grantType != "refresh_token") {
            val error = s"grant_type: '$grantType' is unsupported"
            logger.error(error)
            complete(StatusCodes.BadRequest -> error)
          } else {
            onComplete(tokenService.ask[TokenResponseMessage](ref => RefreshToken(clientId, refreshToken, ref))) {
              case Success(TokenService.TokenResponse(token)) =>
                complete(StatusCodes.OK, TokenMapper.toRepresentation(token).asJson)

              case Success(TokenService.Rejected(error, reason)) =>
                logger.error(s"Rejected with code: $error and reason: $reason")
                complete(StatusCodes.BadRequest -> reason)

              case Success(TokenService.DbFailure(ex)) =>
                logger.error("Exception: ", ex)
                complete(StatusCodes.InternalServerError -> "DataBase problems")

              case Failure(e) =>
                logger.error("Exception: ", e)
                complete(StatusCodes.InternalServerError -> e.getMessage)
            }
          }
        }
      }
    }
  }
}
