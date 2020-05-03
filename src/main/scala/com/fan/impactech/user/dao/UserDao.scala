package com.fan.impactech.user.dao

import akka.Done
import com.fan.impactech.dao.GenericDao
import com.fan.impactech.db.{ExtendedPostgresDriver, HasDatabaseConfig, TableHolder}
import com.fan.impactech.user.dao.domain.UserDTO
import com.fan.impactech.user.mapper.UserMapper
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}
import slick.basic.DatabaseConfig

import scala.concurrent.{ExecutionContext, Future}

class UserDao (implicit val injector: Injector) extends Injectable
  with GenericDao[UserDTO, Done]
  with HasDatabaseConfig[ExtendedPostgresDriver]
  with TableHolder
  with LazyLogging {
  import profile.api._

  private implicit val ec: ExecutionContext = inject[ExecutionContext]('executionContext)
  override protected val dbConfig: DatabaseConfig[ExtendedPostgresDriver] = DatabaseConfig.forConfig[ExtendedPostgresDriver]("database")
  
  override def exists (userName: String): Future[Boolean] = {
    db.run(users.filter(user => user.userName === userName).exists.result)
  }

  override def get (userNames: Seq[String]): Future[Seq[UserDTO]] = {
    db.run(users.filter(_.userName inSet Set(userNames: _*)).result.map(_.map(UserMapper.apply)))
  }

  override def findAll (): Future[Seq[UserDTO]] = {
    db.run(users.result.map(_.map(_.toUserDTO)))
  }

  override def put (entity: UserDTO): Future[Done] = {
    val insertClient = users.insertOrUpdate(UserMapper(entity))

    db.run(insertClient.transactionally).map(_ => Done)
  }

  override def remove (userName: String): Future[Boolean] = {
    db.run(users.filter(_.userName === userName).delete).map(count ⇒ count > 0)
  }
}
