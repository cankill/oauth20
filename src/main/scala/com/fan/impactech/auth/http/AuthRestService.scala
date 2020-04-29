package com.fan.impactech.auth.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, onComplete, path, post, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.util.Timeout
import com.fan.impactech.client.http.domain.ClientCreateRequest
import com.fan.impactech.client.http.validate.ClientValidator
import com.fan.impactech.client.http.validate.ClientValidator.{ClientInvalid, ClientValid, ClientValidatedResponseMessage, ValidateClientRequest}
import com.fan.impactech.client.repo.ClientRepository
import com.fan.impactech.client.repo.ClientRepository._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class AuthRestService (implicit inject: Injector) extends Injectable with LazyLogging {
  private implicit val executionContext: ExecutionContext = inject[ExecutionContext]('executionContext)
  private implicit val actorSystem: ActorSystem[Nothing] = inject[ActorSystem[Nothing]]

  val clientRepo: TypedActorRef[ClientRepository.Protocol] = inject[TypedActorRef[ClientRepository.Protocol]]
  val clientValidator: TypedActorRef[ClientValidator.Protocol] = inject[TypedActorRef[ClientValidator.Protocol]]

  private val config: Config = inject[Config]
  private implicit val timeout: Timeout = 2.minutes

  def myUserPassAuthenticator(credentials: Credentials): Option[String] =
    credentials match {
      case p @ Credentials.Provided(id) if p.verify("p4ssw0rd") => Some(id)
      case _ => None
    }

  def authRoute: Route = Route.seal {
    get {
      path("auth") {
        authenticateBasic(realm = "secure site", myUserPassAuthenticator) { userName =>
          logger.debug(s"Got request to get exist Client: $clientId")
          onComplete(clientRepo.ask[ClientResponseMessage](ref => GetClient(clientId, ref))) {
            case Success(ClientDeleted) =>
              complete(StatusCodes.NoContent)

            case Success(ClientNotFound) =>
              complete(StatusCodes.NotFound)

            case Success(DbFailure(ex)) =>
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
