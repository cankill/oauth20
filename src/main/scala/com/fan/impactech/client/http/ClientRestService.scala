package com.fan.impactech.client.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, onComplete, path, post, _}
import akka.http.scaladsl.server.Route
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
import io.circe.syntax._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ClientRestService (implicit inject: Injector) extends Injectable with LazyLogging {
  private implicit val executionContext: ExecutionContext = inject[ExecutionContext]('executionContext)
  private implicit val actorSystem: ActorSystem[Nothing] = inject[ActorSystem[Nothing]]

  val clientRepo: TypedActorRef[ClientRepository.Protocol] = inject[TypedActorRef[ClientRepository.Protocol]]
  val clientValidator: TypedActorRef[ClientValidator.Protocol] = inject[TypedActorRef[ClientValidator.Protocol]]

  private val config: Config = inject[Config]
  private implicit val timeout: Timeout = 2.minutes

  def clientsManagementRoute: Route =
    post {
      path("client") {
        logger.debug("Got request to create new Client")
        entity(as[ClientCreateRequest]) { clientCreateRequest =>
          onComplete(clientValidator.ask[ClientValidatedResponseMessage](ref => ValidateClientRequest(clientCreateRequest, ref))) {
            case Success(ClientValid(clientDTO)) =>
              onComplete(clientRepo.ask[ClientResponseMessage](ref => AddClient(clientDTO, ref))) {
                case Success(ClientCreated) =>
                  complete(StatusCodes.Created -> clientDTO.clientId)

                case Success(ClientExists) =>
                  complete(StatusCodes.BadRequest -> "Client already exists")

                case Failure(ex) =>
                  logger.error("Failed to add client. Exception: ", ex)
                  complete(StatusCodes.InternalServerError -> ex.getMessage)
              }

            case Success(ClientInvalid(errors)) =>
              complete(StatusCodes.BadRequest -> errors)

            case Failure(ex) =>
              logger.error("Failed to validate incoming request. Exception: ", ex)
              complete(StatusCodes.BadRequest -> ex.getMessage)
          }
        }
      }
    } ~
    get {
      path("client" / Segment ) { clientId =>
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
