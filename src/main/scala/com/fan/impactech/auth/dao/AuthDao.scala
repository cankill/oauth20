package com.fan.impactech.auth.dao

import com.fan.impactech.auth.dao.domain.AuthCodeDTO
import com.fan.impactech.auth.domain.StoredCredentials

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait AuthDao {
  def getUserCredentials (userName: String): Future[Option[StoredCredentials]]
  def getClientCredentials (clientId: String): Future[Option[StoredCredentials]]
  def getClientCallbackUrl (clientId: String): Future[Option[String]]
  def getAuthorizationCode (clientId: String, userName: String, callbackUrl: String): Future[Option[AuthCodeDTO]]
  def makeAuthorizationCode (clientId: String, userName: String, callbackUrl: String, code: String, expiration: FiniteDuration): Future[Boolean]
}
