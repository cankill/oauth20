package com.fan.impactech.auth.dao

import com.fan.impactech.auth.domain.StoredCredentials
import com.fan.impactech.client.dao.{ClientEntry, ClientTableHolder}
import com.fan.impactech.db.{ExtendedPostgresDriver, HasDatabaseConfig}
import com.fan.impactech.user.dao.{UserEntry, UserTableHolder}
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}
import slick.basic.DatabaseConfig

import scala.concurrent.{ExecutionContext, Future}

class AuthDaoImpl (implicit val injector: Injector) extends Injectable
  with AuthDao
  with HasDatabaseConfig[ExtendedPostgresDriver]
  with ClientTableHolder
  with UserTableHolder
  with LazyLogging {
  import profile.api._

  private implicit val ec: ExecutionContext = inject[ExecutionContext]('executionContext)
  override protected val dbConfig: DatabaseConfig[ExtendedPostgresDriver] = DatabaseConfig.forConfig[ExtendedPostgresDriver]("database")
  private val clientQuery = TableQuery[ClientTable]
  private val userQuery = TableQuery[UserTable]

  def toCredentials(clientEntry: ClientEntry): StoredCredentials = {
    StoredCredentials(clientEntry.clientId, clientEntry.secretId)
  }

  def toCredentials(userEntry: UserEntry): StoredCredentials = {
    StoredCredentials(userEntry.userName, userEntry.password)
  }

  override def getClientCredentials(clientId: String): Future[Option[StoredCredentials]] = {
    db.run(clientQuery.filter(_.clientId === clientId).result.headOption).map(_.map(toCredentials))
  }

  override def getUserCredentials(login: String): Future[Option[StoredCredentials]] = {
    db.run(userQuery.filter(_.login === login).result.headOption).map(_.map(toCredentials))
  }
}
