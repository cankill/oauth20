package com.fan.impactech.auth.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef => TypedActorRef}
import akka.http.scaladsl.server.directives.Credentials
import com.fan.impactech.auth.dao.AuthDao
import com.fan.impactech.auth.domain.StoredCredentials
import com.typesafe.config.Config

import scala.collection.immutable
import scala.util.{Failure, Success}

object AuthService {
  def name = "authService"

  sealed trait Protocol
  sealed trait AuthResponseMessage

  final case class Authenticate(id: String, credentials: Credentials.Provided, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol

  case object Authenticated extends AuthResponseMessage
  case object Rejected extends AuthResponseMessage
  case class DbFailure(ex: Throwable) extends AuthResponseMessage

  private final case class WrappedAuthResult(result: AuthResponseMessage, replyTo: TypedActorRef[AuthResponseMessage]) extends Protocol

  private case class State(workers: immutable.Map[String, String])
  private object State {
    def empty: State = State(Map.empty)
  }

  def behaviour(config: Config, authDao: AuthDao): Behavior[Protocol] = {
    Behaviors.setup[Protocol] { implicit context ⇒
      val log = context.log // thread safe copy

      def readyState (state: State): Behavior[Protocol] = Behaviors.receiveMessage {
        case Authenticate(id, credentials, sender) =>
          log.debug(s"""Received message: Authenticate($id, $credentials, $sender)""")

          context.pipeToSelf(authDao.getClientCredentials(id)) {
            case Success(Some(StoredCredentials(_, passwd))) if credentials.verify(passwd) =>
              WrappedAuthResult(Authenticated, sender)

            case Success(_) => WrappedAuthResult(Rejected, sender)

            case Failure(ex) => WrappedAuthResult(DbFailure(ex), sender)
          }

          Behaviors.same

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
}
