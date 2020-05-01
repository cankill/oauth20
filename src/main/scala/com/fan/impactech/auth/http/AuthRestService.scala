package com.fan.impactech.auth.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, onComplete, path, post, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.util.Timeout
import com.fan.impactech.auth.service.AuthService
import com.fan.impactech.auth.service.AuthService.{AuthResponseMessage, Authenticate, Authenticated, DbFailure, Rejected}
import com.fan.impactech.client.http.validate.ClientValidator
import com.fan.impactech.client.repo.ClientRepository
import com.fan.impactech.client.repo.ClientRepository.{ClientDeleted, ClientFound, ClientNotFound, ClientResponseMessage, GetClient}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import io.circe.generic.auto._
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class AuthRestService (implicit inject: Injector) extends Injectable with LazyLogging {
  private implicit val executionContext: ExecutionContext = inject[ExecutionContext]('executionContext)
  private implicit val actorSystem: ActorSystem[Nothing] = inject[ActorSystem[Nothing]]

  val authService: TypedActorRef[AuthService.Protocol] = inject[TypedActorRef[AuthService.Protocol]]
  val clientRepo: TypedActorRef[ClientRepository.Protocol] = inject[TypedActorRef[ClientRepository.Protocol]]
  val clientValidator: TypedActorRef[ClientValidator.Protocol] = inject[TypedActorRef[ClientValidator.Protocol]]

  private val config: Config = inject[Config]
  private implicit val timeout: Timeout = 2.minutes

  def myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] = credentials match {
      case p @ Credentials.Provided(id) =>
        logger.debug(s"Got request to authenticate: $id")
        authService.ask[AuthResponseMessage](ref => Authenticate(id, p, ref)).transform {
          case Success(Authenticated) =>
            logger.error(s"Authenticated")
            Success(Some(id))

          case Success(Rejected) =>
            logger.error(s"Rejected")
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

  def authRoute: Route = Route.seal {
    get {
      path("auth") {
        authenticateBasicAsync(realm = "oauth20", myUserPassAuthenticator) { userName =>
          logger.debug(s"Got request to authenticate: $userName")
          onComplete(clientRepo.ask[ClientResponseMessage](ref => GetClient(userName, ref))) {
            case Success(ClientFound(clients)) =>
              import com.fan.impactech.client.dao.domain.ClientState._
              complete(StatusCodes.OK -> clients.asJson)

            case Success(ClientNotFound) =>
              complete(StatusCodes.NotFound)

            case Success(ClientRepository.DbFailure(ex)) =>
              logger.error("Exception: ", ex)
              complete(StatusCodes.InternalServerError)

            case Failure(e) =>
              logger.error("Exception: ", e)
              complete(StatusCodes.InternalServerError -> e.getMessage)
          }
        }
      }
    }
  }
}
