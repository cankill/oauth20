package com.fan.impactech.token.dao

import java.time.Instant

import com.fan.impactech.auth.dao.domain.AuthCodeDTO
import com.fan.impactech.auth.domain.StoredCredentials
import com.fan.impactech.auth.mapper.AuthMapper
import com.fan.impactech.db
import com.fan.impactech.db.{AuthCodeEntry, ClientEntry, ExtendedPostgresDriver, HasDatabaseConfig, TableHolder, TokenEntry, UserEntry}
import com.fan.impactech.token.dao.domain.TokenDTO
import com.fan.impactech.token.mapper.TokenMapper
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}
import slick.basic.DatabaseConfig

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class TokenDaoImpl (implicit val injector: Injector) extends Injectable
  with TokenDao
  with HasDatabaseConfig[ExtendedPostgresDriver]
  with TableHolder
  with LazyLogging {
  import profile.api._

  private implicit val ec: ExecutionContext = inject[ExecutionContext]('executionContext)
  override protected val dbConfig: DatabaseConfig[ExtendedPostgresDriver] = DatabaseConfig.forConfig[ExtendedPostgresDriver]("database")

  def toCredentials(clientEntry: ClientEntry): StoredCredentials = {
    StoredCredentials(clientEntry.clientId, clientEntry.secretId)
  }

  override def getClientCredentials(clientId: String): Future[Option[StoredCredentials]] = {
    db.run(clients.filter(_.clientId === clientId).result.headOption).map(_.map(toCredentials))
  }

  override def getAuthCode (clientId: String, code: String): Future[Option[AuthCodeDTO]] = {
    db.run(authCode.filter(row => row.clientId === clientId
                                  && row.code === code
                                  && row.expireAt > Instant.now
                                  && !row.used).result.headOption).map(_.map(AuthMapper(_)))
  }
  
  override def getToken (clientId: String, userName: String, callbackUrl: String): Future[Option[TokenDTO]] = {
    db.run(tokens.filter(row => row.clientId === clientId
                                && row.userName === userName
                                && row.callbackUrl === callbackUrl
                                && row.expireAt > Instant.now
                                && !row.revoked).result.headOption).map(_.map(TokenMapper(_)))
  }

  override def getToken (refreshToken: String): Future[Option[TokenDTO]] = {
    db.run(tokens.filter(row => row.refreshToken === refreshToken
                                && !row.revoked).result.headOption).map(_.map(TokenMapper(_)))
  }

  override def getValidToken (accessToken: String): Future[Option[TokenDTO]] = {
    db.run(tokens.filter(row => row.accessToken === accessToken
                                && !row.revoked).result.headOption).map(_.map(TokenMapper(_)))
  }

  override def makeToken (clientId: String, userName: String, callbackUrl: String, code: String, accessToken: String, refreshToken: String, expiration: FiniteDuration): Future[TokenDTO] = {
    val now = Instant.now
    val expiredAt = now.plusSeconds(expiration.toSeconds)
    val updateCode = authCode.filter(ac => ac.code === code)
                             .map(ac => ac.used)
                             .update(true)
    val newToken = TokenEntry(clientId, userName, callbackUrl, accessToken, refreshToken, false, now, expiredAt)
    val insertToken = tokens += newToken

    db.run({
             for {
               _ <- updateCode
               _ <- insertToken
             } yield newToken.toTokenDTO
    }.transactionally)
  }

  def regenerateToken (refreshToken: String, clientId: String, userName: String, callbackUrl: String, newAccessToken: String, newRefreshToken: String, expiration: FiniteDuration): Future[TokenDTO] = {
    val now = Instant.now
    val expiredAt = now.plusSeconds(expiration.toSeconds)
    val updateRevokedToken = tokens.filter(t => t.refreshToken === refreshToken)
                                   .map(ac => ac.revoked)
                                   .update(true)
    val newToken = TokenEntry(clientId, userName, callbackUrl, newAccessToken, newRefreshToken, false, now, expiredAt)
    val insertToken = tokens += newToken

    db.run({
             for {
               _ <- updateRevokedToken
               _ <- insertToken
             } yield newToken.toTokenDTO
           }.transactionally)
  }
}
