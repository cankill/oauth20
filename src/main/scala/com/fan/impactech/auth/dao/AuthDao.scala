package com.fan.impactech.auth.dao

import com.fan.impactech.auth.domain.StoredCredentials

import scala.concurrent.Future

trait AuthDao {
  def getUserCredentials(id: String): Future[Option[StoredCredentials]]
  def getClientCredentials(id: String): Future[Option[StoredCredentials]]
}
