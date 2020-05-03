package com.fan.impactech.user.http.validate

import java.time.Instant

import cats.data.Validated._
import cats.data.ValidatedNec
import cats.implicits._
import com.fan.impactech.user.dao.domain.UserDTO

sealed trait UserValidatorNec {
  type ValidationResult[A] = ValidatedNec[UserDomainValidation, A]

  private def validateUserName(login: String): ValidationResult[String] =
    if (login.matches("^[a-zA-Z0-9]+$")) login.validNec else UserNameHasSpecialCharacters.invalidNec

  private def validatePassword(password: String): ValidationResult[String] =
    if (password.matches("(?=^.{10,}$)((?=.*\\d)|(?=.*\\W+))(?![.\\n])(?=.*[A-Z])(?=.*[a-z]).*$")) password.validNec
    else PasswordDoesNotMeetCriteria.invalidNec

  private def validateName(name: String): ValidationResult[String] = name.validNec
  private def validateCreated(): ValidationResult[Instant] = Instant.now.validNec
  private def validateModified(): ValidationResult[Option[Instant]] = None.validNec

  def validateUserCreateRequest (user_name: String, password: String, name: String): ValidationResult[UserDTO] = {
    (validateUserName(user_name),
     validatePassword(password),
     validateName(name),
     validateCreated(),
     validateModified()).mapN(UserDTO)
  }
}

object UserValidatorNec extends UserValidatorNec

