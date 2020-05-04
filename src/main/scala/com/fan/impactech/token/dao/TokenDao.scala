package com.fan.impactech.token.dao

import com.fan.impactech.auth.dao.domain.AuthCodeDTO
import com.fan.impactech.auth.domain.StoredCredentials
import com.fan.impactech.token.dao.domain.TokenDTO

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait TokenDao {
  def getClientCredentials (clientId: String): Future[Option[StoredCredentials]]
  def getAuthCode (clientId: String, code: String): Future[Option[AuthCodeDTO]]
  def getToken (clientId: String, userName: String, callbackUrl: String): Future[Option[TokenDTO]]
  def getToken (refreshToken: String): Future[Option[TokenDTO]]
  def getValidToken (acceessToken: String): Future[Option[TokenDTO]]
  def makeToken (clientId: String, userName: String, callbackUrl: String, code: String, accessToken: String, refreshToken: String, expiration: FiniteDuration): Future[TokenDTO]
  def regenerateToken (refreshToken: String, clientId: String, userName: String, callbackUrl: String, newAccessToken: String, newRefreshToken: String, expiration: FiniteDuration): Future[TokenDTO]
}
