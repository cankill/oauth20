package com.fan.impactech.client.http.validate

sealed trait ClientDomainValidation {
  def errorMessage: String
}

case object ClientIdHasSpecialCharacters extends ClientDomainValidation {
  def errorMessage: String = "client_id cannot contain special characters."
}

case object ApplicationIdHasSpecialCharacters extends ClientDomainValidation {
  def errorMessage: String = "application_id cannot contain special characters."
}

case object SecretIdDoesNotMeetCriteria extends ClientDomainValidation {
  def errorMessage: String = "secret_id must be at least 10 characters long, including an uppercase and a lowercase letter, one number and one special character."
}

case object UrlIsInvalid extends ClientDomainValidation {
  def errorMessage: String = "Provided callback URL is not valid."
}