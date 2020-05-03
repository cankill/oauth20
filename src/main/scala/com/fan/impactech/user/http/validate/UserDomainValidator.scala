package com.fan.impactech.user.http.validate

sealed trait UserDomainValidation {
  def errorMessage: String
}

case object UserNameHasSpecialCharacters extends UserDomainValidation {
  def errorMessage: String = "user_name cannot contain special characters."
}

case object PasswordDoesNotMeetCriteria extends UserDomainValidation {
  def errorMessage: String = "password must be at least 10 characters long, including an uppercase and a lowercase letter, one number and one special character."
}