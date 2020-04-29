package com.fan.impactech.client.repo

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef => TypedActorRef}
import com.fan.impactech.client.dao.domain.ClientDTO
import com.fan.impactech.dao.GenericDao
import com.typesafe.config.Config

import scala.collection.immutable
import scala.util.{Failure, Success}

object ClientRepository {
  def name = "clientRepository"

  sealed trait Protocol
  sealed trait ClientResponseMessage
  final case class AddClient(newClient: ClientDTO, replyTo: TypedActorRef[ClientResponseMessage]) extends Protocol
  final case class GetClient(id: String, replyTo: TypedActorRef[ClientResponseMessage]) extends Protocol
  final case class DeleteClient(id: String, replyTo: TypedActorRef[ClientResponseMessage]) extends Protocol

  case object ClientCreated extends ClientResponseMessage
  case object ClientDeleted extends ClientResponseMessage
  case class ClientFound(clientDTO: Seq[ClientDTO]) extends ClientResponseMessage
  case object ClientNotFound extends ClientResponseMessage
  case class DbFailure(ex: Throwable) extends ClientResponseMessage
  case object ClientExists extends ClientResponseMessage

  private final case class CreateClient(newClient: ClientDTO, replyTo: TypedActorRef[ClientResponseMessage]) extends Protocol

  private final case class WrappedUpdateResult(result: ClientResponseMessage, replyTo: TypedActorRef[ClientResponseMessage]) extends Protocol

  private case class State(workers: immutable.Map[String, String])
  private object State {
    def empty: State = State(Map.empty)
  }

  def behaviour(config: Config, clientsDao: GenericDao[ClientDTO, Done]): Behavior[Protocol] = {
    Behaviors.withStash(1000) { stashBuffer =>
      Behaviors.setup[Protocol] { implicit context ⇒
        val log = context.log // thread safe copy

        def readyState (state: State): Behavior[Protocol] = Behaviors.receiveMessage {
          case GetClient(clientId, sender) =>
            log.debug(s"""Received message: GetClient($clientId, $sender)""")

            context.pipeToSelf(clientsDao.get(Seq(clientId))) {
              case Success(Nil) => WrappedUpdateResult(ClientNotFound, sender)
              case Success(clients) => WrappedUpdateResult(ClientFound(clients), sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }

            Behaviors.same

          case AddClient(newClient, sender) =>
            log.debug(s"""Received message:AddClient($newClient, $sender)""")

            context.pipeToSelf(clientsDao.exists(newClient.clientId)) {
              case Success(true) => WrappedUpdateResult(ClientExists, sender)
              case Success(false) => CreateClient(newClient, sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }
            
            createState(state)

          case DeleteClient(clientId, sender) =>
            log.debug(s"""Received message: DeleteClient($clientId, $sender)""")

            context.pipeToSelf(clientsDao.remove(clientId)) {
              case Success(true) => WrappedUpdateResult(ClientDeleted, sender)
              case Success(false) => WrappedUpdateResult(ClientNotFound, sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }

            createState(state)

          case WrappedUpdateResult(result, replyTo) =>
            replyTo ! result
            Behaviors.same

          case msg =>
            log.debug(s"""Ignore received message in a ready State: $msg""")
            Behaviors.same
        }

        def createState (state: State): Behavior[Protocol] = Behaviors.receiveMessage {
          case CreateClient(newClient, sender) =>
            context.pipeToSelf(clientsDao.put(newClient)) {
              case Success(Done) => WrappedUpdateResult(ClientCreated, sender)
              case Failure(ex) => WrappedUpdateResult(DbFailure(ex), sender)
            }
            Behaviors.same

          case WrappedUpdateResult(result, replyTo) =>
            replyTo ! result
            stashBuffer.unstashAll(readyState(state))

          case getClientMsg @ GetClient(clientId, sender) =>
            log.debug(s"""Will stash received message: GetClient($clientId, $sender)""")
            stashBuffer.stash(getClientMsg)
            Behaviors.same

          case addClientMsg @ AddClient(newClient, sender) =>
            log.debug(s"""Will stash received message: AddClient($newClient, $sender)""")
            stashBuffer.stash(addClientMsg)
            Behaviors.same

          case deleteClientMsg @ DeleteClient(clientId, sender) =>
            log.debug(s"""Will stash received message: DeleteClient($clientId, $sender)""")
            stashBuffer.stash(deleteClientMsg)
            Behaviors.same

          case msg =>
            log.debug(s"""Ignore received message in a create State: $msg""")
            Behaviors.same
        }

        readyState(State.empty)
      }
    }
  }
}
