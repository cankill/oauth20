package com.fan.impactech.client.http.validate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef => TypedActorRef}
import cats.data.Validated.{Invalid, Valid}
import com.fan.impactech.client.dao.domain.ClientDTO
import com.fan.impactech.client.http.domain.ClientCreateRequest
import com.typesafe.config.Config

object ClientValidator {
  def name = "clientValidator"

  sealed trait Protocol
  sealed trait ClientValidatedResponseMessage
  final case class ValidateClientRequest(clientCreateRequest: ClientCreateRequest,
                                         replyTo: TypedActorRef[ClientValidatedResponseMessage]) extends Protocol

  case class ClientValid(client: ClientDTO) extends ClientValidatedResponseMessage
  case class ClientInvalid(errors: Seq[String]) extends ClientValidatedResponseMessage

  def behaviour(config: Config): Behavior[Protocol] = {
    Behaviors.setup[Protocol] { implicit context ⇒
      val log = context.log // thread safe copy

      def readyState (): Behavior[Protocol] = Behaviors.receiveMessage {
        case ValidateClientRequest(clientCreateRequest, sender) =>
          log.debug(s"""Received message: ValidateClientRequest($clientCreateRequest, $sender)""")
          val vlidatedResult = ClientValidatorNec.validateClientCreateRequest(clientCreateRequest.client_id,
                                                                              clientCreateRequest.application_id,
                                                                              clientCreateRequest.secret_id,
                                                                              clientCreateRequest.callback_url)
          vlidatedResult match {
            case Valid(client) =>
              log.debug(s"""Client create request validated successfully""")
              sender ! ClientValid(client)
            case Invalid(errors) =>
              log.debug(s"""Client create request invalid with messages: $errors""")
              sender ! ClientInvalid(errors.map(_.errorMessage).iterator.toSeq)
          }
          
          Behaviors.same
      }

      readyState()
    }
  }
}
