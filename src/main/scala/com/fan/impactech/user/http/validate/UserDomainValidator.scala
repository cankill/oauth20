package com.fan.impactech.user.http.validate

sealed trait UserDomainValidation {
  def errorMessage: String
}

case object UserLoginHasSpecialCharacters extends UserDomainValidation {
  def errorMessage: String = "login cannot contain special characters."
}

case object PasswordDoesNotMeetCriteria extends UserDomainValidation {
  def errorMessage: String = "password must be at least 10 characters long, including an uppercase and a lowercase letter, one number and one special character."
}