package com.fan.impactech.user.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, onComplete, path, post, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.fan.impactech.user.http.domain.UserCreateRequest
import com.fan.impactech.user.http.validate.UserValidator
import com.fan.impactech.user.http.validate.UserValidator.{UserInvalid, UserValid, UserValidatedResponseMessage, ValidateUserRequest}
import com.fan.impactech.user.repo.UserRepository
import com.fan.impactech.user.repo.UserRepository.{AddUser, DbFailure, DeleteUser, GetUser, UserCreated, UserDeleted, UserExists, UserFound, UserNotFound, UserResponseMessage}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class UserRestService (implicit inject: Injector) extends Injectable with LazyLogging {
  private implicit val executionContext: ExecutionContext = inject[ExecutionContext]('executionContext)
  private implicit val actorSystem: ActorSystem[Nothing] = inject[ActorSystem[Nothing]]

  val userRepo: TypedActorRef[UserRepository.Protocol] = inject[TypedActorRef[UserRepository.Protocol]]
  val userValidator: TypedActorRef[UserValidator.Protocol] = inject[TypedActorRef[UserValidator.Protocol]]

  private val config: Config = inject[Config]
  private implicit val timeout: Timeout = 2.minutes

  def userManagementRoute: Route =
    post {
      path("user") {
        logger.debug("Got request to create new User")
        entity(as[UserCreateRequest]) { userCreateRequest =>
          onComplete(userValidator.ask[UserValidatedResponseMessage](ref => ValidateUserRequest(userCreateRequest, ref))) {
            case Success(UserValid(userDTO)) =>
              onComplete(userRepo.ask[UserResponseMessage](ref => AddUser(userDTO, ref))) {
                case Success(UserCreated) =>
                  complete(StatusCodes.Created -> userDTO.login)

                case Success(UserExists) =>
                  complete(StatusCodes.BadRequest -> "User already exists")

                case Failure(ex) =>
                  logger.error("Failed to add user. Exception: ", ex)
                  complete(StatusCodes.InternalServerError -> ex.getMessage)
              }

            case Success(UserInvalid(errors)) =>
              complete(StatusCodes.BadRequest -> errors)

            case Failure(ex) =>
              logger.error("Failed to validate incoming request. Exception: ", ex)
              complete(StatusCodes.BadRequest -> ex.getMessage)
          }
        }
      }
    } ~
    get {
      path("user" / Segment ) { userId =>
        logger.debug(s"Got request to get exist User: $userId")
        onComplete(userRepo.ask[UserResponseMessage](ref => GetUser(userId, ref))) {
          case Success(UserFound(users)) =>
            complete(StatusCodes.OK -> users.asJson)

          case Success(UserNotFound) =>
            complete(StatusCodes.NotFound)

          case Success(DbFailure(ex)) =>
            logger.error("Exception: ", ex)
            complete(StatusCodes.InternalServerError)

          case Failure(e) =>
            logger.error("Exception: ", e)
            complete(StatusCodes.InternalServerError -> e.getMessage)
        }
      }
    } ~
    delete {
      path("user" / Segment ) { userId =>
        logger.debug(s"Got request to delete exist User: $userId")
        onComplete(userRepo.ask[UserResponseMessage](ref => DeleteUser(userId, ref))) {
          case Success(UserDeleted) =>
            complete(StatusCodes.NoContent)

          case Success(UserNotFound) =>
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
