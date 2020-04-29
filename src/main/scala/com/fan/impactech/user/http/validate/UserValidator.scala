package com.fan.impactech.user.http.validate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, ActorRef => TypedActorRef}
import cats.data.Validated.{Invalid, Valid}
import com.fan.impactech.client.dao.domain.ClientDTO
import com.fan.impactech.client.http.domain.ClientCreateRequest
import com.fan.impactech.client.http.validate.ClientValidator.ClientValid
import com.fan.impactech.user.dao.domain.UserDTO
import com.fan.impactech.user.http.domain.UserCreateRequest
import com.typesafe.config.Config

object UserValidator {
  def name = "userValidator"

  sealed trait Protocol
  sealed trait UserValidatedResponseMessage
  final case class ValidateUserRequest(userCreateRequest: UserCreateRequest,
                                       replyTo: TypedActorRef[UserValidatedResponseMessage]) extends Protocol

  case class UserValid(user: UserDTO) extends UserValidatedResponseMessage
  case class UserInvalid(errors: Seq[String]) extends UserValidatedResponseMessage

  def behaviour(config: Config): Behavior[Protocol] = {
    Behaviors.setup[Protocol] { implicit context ⇒
      val log = context.log // thread safe copy

      def readyState (): Behavior[Protocol] = Behaviors.receiveMessage {
        case ValidateUserRequest(userCreateRequest, sender) =>
          log.debug(s"""Received message: ValidateUserRequest($userCreateRequest, $sender)""")
          val vlidatedResult = UserValidatorNec.validateUserCreateRequest(userCreateRequest.login,
                                                                          userCreateRequest.password,
                                                                          userCreateRequest.user_name)
          vlidatedResult match {
            case Valid(user) =>
              log.debug(s"""User create request validated successfully""")
              sender ! UserValid(user)
            case Invalid(errors) =>
              log.debug(s"""User create request invalid with messages: $errors""")
              sender ! UserInvalid(errors.map(_.errorMessage).iterator.toSeq)
          }
          
          Behaviors.same
      }

      readyState()
    }
  }
}
