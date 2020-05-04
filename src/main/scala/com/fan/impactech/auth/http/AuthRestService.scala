package com.fan.impactech.auth.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.fan.impactech.auth.service.AuthService
import com.fan.impactech.auth.service.AuthService.{AuthResponseMessage, Authenticate, Authenticated, Authorize, DbFailure, Rejected}
import com.fan.impactech.client.http.validate.ClientValidator
import com.fan.impactech.client.repo.ClientRepository
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class AuthRestService (implicit inject: Injector) extends Injectable with FailFastCirceSupport with LazyLogging {
  private implicit val executionContext: ExecutionContext = inject[ExecutionContext]('executionContext)
  private implicit val actorSystem: ActorSystem[Nothing] = inject[ActorSystem[Nothing]]

  val authService: TypedActorRef[AuthService.Protocol] = inject[TypedActorRef[AuthService.Protocol]]

  private val config: Config = inject[Config]
  private implicit val timeout: Timeout = 2.minutes

  def
  myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] = credentials match {
      case p @ Credentials.Provided(id) =>
        logger.debug(s"Got request to authenticate: $id")
        authService.ask[AuthResponseMessage](ref => Authenticate(id, p, ref)).transform {
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

  def authRoute: Route =
    get {
        path("oauth20" / "authorize") {
          authenticateBasicAsync(realm = "oauth20", myUserPassAuthenticator) { userName =>
            parameters('response_type, 'client_id, 'redirect_uri.?, 'scope.?, 'state.?).as(AuthService.AuthorizationRequest) { authRequest =>
              logger.debug(s"Got request to authorize client: $authRequest to use resources for user: $userName")
              onComplete(authService.ask[AuthResponseMessage](ref => Authorize(userName, authRequest, ref))) {
                case Success(AuthService.Authorized(redirectUrl)) =>
                  redirect(redirectUrl, StatusCodes.Found)

                case Success(AuthService.Unauthorized(redirectUrl)) =>
                  redirect(redirectUrl, StatusCodes.Found)

                case Success(Rejected(error, reason)) =>
                  logger.error(s"Rejected with code: $error and reason: $reason")
                  complete(StatusCodes.BadRequest -> reason)

                case Success(AuthService.DbFailure(ex)) =>
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
