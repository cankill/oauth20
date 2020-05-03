package com.fan.impactech.client.http.validate

import java.time.Instant

import cats.data.ValidatedNec
import cats.data.Validated._
import cats.implicits._
import com.fan.impactech.client.dao.domain.{ClientDTO, ClientState}
import io.lemonlabs.uri.Url

import scala.util.Try

sealed trait ClientValidatorNec {
  type ValidationResult[A] = ValidatedNec[ClientDomainValidation, A]

  private def validateClientId(clientId: String): ValidationResult[String] =
    if (clientId.matches("^[a-zA-Z0-9]+$")) clientId.validNec else ClientIdHasSpecialCharacters.invalidNec

  private def validateSecretId(secretId: String): ValidationResult[String] =
    if (secretId.matches("(?=^.{10,}$)((?=.*\\d)|(?=.*\\W+))(?![.\\n])(?=.*[A-Z])(?=.*[a-z]).*$")) secretId.validNec
    else SecretIdDoesNotMeetCriteria.invalidNec

  private def validateCallbackUrl(callbackUrl: String): ValidationResult[String] = {
    val paresedUrlOpt = Url.parseOption(callbackUrl)
    if (paresedUrlOpt.isDefined) callbackUrl.validNec else UrlIsInvalid.invalidNec
  }

  private def validateClientState(): ValidationResult[ClientState] = ClientState.Active.validNec
  private def validateCreated(): ValidationResult[Instant] = Instant.now.validNec
  private def validateModified(): ValidationResult[Option[Instant]] = None.validNec

  def validateClientCreateRequest (clientId: String, secretId: String, callbackUrl: String): ValidationResult[ClientDTO] = {
    (validateClientId(clientId),
     validateSecretId(secretId),
     validateCallbackUrl(callbackUrl),
     validateClientState(),
     validateCreated(),
     validateModified()).mapN(ClientDTO)
  }
}

object ClientValidatorNec extends ClientValidatorNec