package com.fan.impactech.user.repo

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef => TypedActorRef}
import com.fan.impactech.dao.GenericDao
import com.fan.impactech.user.dao.domain.UserDTO
import com.typesafe.config.Config

import scala.util.{Failure, Success}

object UserRepository {
  def name = "userRepository"

  sealed trait Protocol
  sealed trait UserResponseMessage
  final case class AddUser (newUser: UserDTO, replyTo: TypedActorRef[UserResponseMessage]) extends Protocol
  final case class GetUser (id: String, replyTo: TypedActorRef[UserResponseMessage]) extends Protocol
  final case class DeleteUser (id: String, replyTo: TypedActorRef[UserResponseMessage]) extends Protocol

  case object UserCreated extends UserResponseMessage
  case object UserDeleted extends UserResponseMessage
  case class UserFound (userDTO: Seq[UserDTO]) extends UserResponseMessage
  case object UserNotFound extends UserResponseMessage
  case class DbFailure(ex: Throwable) extends UserResponseMessage
  case object UserExists extends UserResponseMessage

  private final case class CreateUser(newUser: UserDTO, replyTo: TypedActorRef[UserResponseMessage]) extends Protocol

  private final case class WrappedUpdateResult(result: UserResponseMessage, replyTo: TypedActorRef[UserResponseMessage]) extends Protocol

  def behaviour(config: Config, userDao: GenericDao[UserDTO, Done]): Behavior[Protocol] = {
    Behaviors.withStash(1000) { stashBuffer =>
      Behaviors.setup[Protocol] { implicit context ⇒
        val log = context.log // thread safe copy

        def readyState (): Behavior[Protocol] = Behaviors.receiveMessage {
          case GetUser(login, sender) =>
            log.debug(s"""Received message: GetUser($login, $sender)""")

            context.pipeToSelf(userDao.get(Seq(login))) {
              case Success(Nil) => WrappedUpdateResult(UserNotFound, sender)
              case Success(users) => WrappedUpdateResult(UserFound(users), sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }

            Behaviors.same

          case AddUser(newUser, sender) =>
            log.debug(s"""Received message: AddUser($newUser, $sender)""")

            context.pipeToSelf(userDao.exists(newUser.login)) {
              case Success(true) => WrappedUpdateResult(UserExists, sender)
              case Success(false) => CreateUser(newUser, sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }

            createState()

          case DeleteUser(login, sender) =>
            log.debug(s"""Received message: DeleteUser($login, $sender)""")

            context.pipeToSelf(userDao.remove(login)) {
              case Success(true) => WrappedUpdateResult(UserDeleted, sender)
              case Success(false) => WrappedUpdateResult(UserNotFound, sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }

            createState()

          case WrappedUpdateResult(result, replyTo) =>
            replyTo ! result
            Behaviors.same

          case msg =>
            log.debug(s"""Ignore received message in a ready State: $msg""")
            Behaviors.same
        }

        def createState (): Behavior[Protocol] = Behaviors.receiveMessage {
          case CreateUser(newUser, sender) =>
            context.pipeToSelf(userDao.put(newUser)) {
              case Success(Done) => WrappedUpdateResult(UserCreated, sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }
            Behaviors.same

          case WrappedUpdateResult(result, replyTo) =>
            replyTo ! result
            stashBuffer.unstashAll(readyState())
            
          case getUsersg @ GetUser(login, sender) =>
            log.debug(s"""Will stash received message: GetUser($login, $sender)""")
            stashBuffer.stash(getUsersg)
            Behaviors.same

          case addUserMsg @ AddUser(newUser, sender) =>
            log.debug(s"""Will stash received message: AddUser($newUser, $sender)""")
            stashBuffer.stash(addUserMsg)
            Behaviors.same

          case deleteUserMsg @ DeleteUser(login, sender) =>
            log.debug(s"""Will stash received message: DeleteUser($login, $sender)""")
            stashBuffer.stash(deleteUserMsg)
            Behaviors.same

          case msg =>
            log.debug(s"""Ignore received message in a create State: $msg""")
            Behaviors.same
        }

        readyState()
      }
    }
  }
}
