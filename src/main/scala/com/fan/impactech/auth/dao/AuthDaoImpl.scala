package com.fan.impactech.auth.dao

import java.time.Instant

import com.fan.impactech.auth.dao.domain.AuthCodeDTO
import com.fan.impactech.auth.domain.StoredCredentials
import com.fan.impactech.auth.mapper.AuthMapper
import com.fan.impactech.db.{AuthCodeEntry, ClientEntry, ExtendedPostgresDriver, HasDatabaseConfig, TableHolder, UserEntry}
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}
import slick.basic.DatabaseConfig

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class AuthDaoImpl (implicit val injector: Injector) extends Injectable
  with AuthDao
  with HasDatabaseConfig[ExtendedPostgresDriver]
  with TableHolder
  with LazyLogging {
  import profile.api._

  private implicit val ec: ExecutionContext = inject[ExecutionContext]('executionContext)
  override protected val dbConfig: DatabaseConfig[ExtendedPostgresDriver] = DatabaseConfig.forConfig[ExtendedPostgresDriver]("database")

  def toCredentials(clientEntry: ClientEntry): StoredCredentials = {
    StoredCredentials(clientEntry.clientId, clientEntry.secretId)
  }

  def toCredentials(userEntry: UserEntry): StoredCredentials = {
    StoredCredentials(userEntry.userName, userEntry.password)
  }
  
  override def getClientCredentials(clientId: String): Future[Option[StoredCredentials]] = {
    db.run(clients.filter(_.clientId === clientId).result.headOption).map(_.map(toCredentials))
  }

  override def getUserCredentials(userName: String): Future[Option[StoredCredentials]] = {
    db.run(users.filter(_.userName === userName).result.headOption).map(_.map(toCredentials))
  }

  override def getClientCallbackUrl (clientId: String): Future[Option[String]] = {
    db.run(clients.filter(_.clientId === clientId).result.headOption).map(_.map(_.callbackUrl))
  }

  override def getAuthorizationCode (clientId: String, userName: String, callbackUrl: String): Future[Option[AuthCodeDTO]] = {
    db.run(authCode.filter(row => row.clientId === clientId
                                  && row.userName === userName
                                  && row.callbackUrl === callbackUrl
                                  && row.expireAt > Instant.now).result.headOption).map(_.map(AuthMapper(_)))
  }

  def makeAuthorizationCode (clientId: String, userName: String, callbackUrl: String, code: String, expiration: FiniteDuration): Future[Boolean] = {
    val now = Instant.now
    val expiredAt = now.plusSeconds(expiration.toSeconds)
    val insertCode = authCode += AuthCodeEntry(clientId, callbackUrl, code, userName, now, expiredAt, None)
    db.run(insertCode).map(_ > 0)
  }
}
