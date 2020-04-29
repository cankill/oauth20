package com.fan.impactech.user.dao

import akka.Done
import com.fan.impactech.dao.GenericDao
import com.fan.impactech.db.{ExtendedPostgresDriver, HasDatabaseConfig}
import com.fan.impactech.user.dao.domain.UserDTO
import com.fan.impactech.user.mapper.UserMapper
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}
import slick.basic.DatabaseConfig

import scala.concurrent.{ExecutionContext, Future}

class UserDao (implicit val injector: Injector) extends Injectable
  with GenericDao[UserDTO, Done]
  with HasDatabaseConfig[ExtendedPostgresDriver]
  with UserTableHolder
  with LazyLogging {
  import profile.api._

  private implicit val ec: ExecutionContext = inject[ExecutionContext]('executionContext)
  override protected val dbConfig: DatabaseConfig[ExtendedPostgresDriver] = DatabaseConfig.forConfig[ExtendedPostgresDriver]("database")
  private val query = TableQuery[UserTable]

  override def exists (login: String): Future[Boolean] = {
    db.run(query.filter(user => user.login === login).exists.result)
  }

  override def get (logins: Seq[String]): Future[Seq[UserDTO]] = {
    db.run(query.filter(_.login inSet Set(logins: _*)).result.map(_.map(UserMapper.apply)))
  }

  override def findAll (): Future[Seq[UserDTO]] = {
    db.run(query.result.map(_.map(_.toUserDTO)))
  }

  override def put (entity: UserDTO): Future[Done] = {
    val insertClient = query.insertOrUpdate(UserMapper(entity))

    db.run(insertClient.transactionally).map(_ => Done)
  }

  override def remove (login: String): Future[Boolean] = {
    db.run(query.filter(_.login === login).delete).map(count ⇒ count > 0)
  }
}
