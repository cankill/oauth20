package com.fan.impactech.user.http.validate

import java.time.Instant

import cats.data.Validated._
import cats.data.ValidatedNec
import cats.implicits._
import com.fan.impactech.user.dao.domain.UserDTO

sealed trait UserValidatorNec {
  type ValidationResult[A] = ValidatedNec[UserDomainValidation, A]

  private def validateLogin(login: String): ValidationResult[String] =
    if (login.matches("^[a-zA-Z0-9]+$")) login.validNec else UserLoginHasSpecialCharacters.invalidNec

  private def validatePassword(password: String): ValidationResult[String] =
    if (password.matches("(?=^.{10,}$)((?=.*\\d)|(?=.*\\W+))(?![.\\n])(?=.*[A-Z])(?=.*[a-z]).*$")) password.validNec
    else PasswordDoesNotMeetCriteria.invalidNec

  private def validateUserName(userName: String): ValidationResult[String] = userName.validNec
  private def validateCreated(): ValidationResult[Instant] = Instant.now.validNec
  private def validateModified(): ValidationResult[Option[Instant]] = None.validNec

  def validateUserCreateRequest (login: String, password: String, userName: String): ValidationResult[UserDTO] = {
    (validateLogin(login),
     validatePassword(password),
     validateUserName(userName),
     validateCreated(),
     validateModified()).mapN(UserDTO)
  }
}

object UserValidatorNec extends UserValidatorNec

