package com.fan.impactech.client.dao

import akka.Done
import com.fan.impactech.client.dao.domain.ClientDTO
import com.fan.impactech.dao.GenericDao
import com.fan.impactech.db.{ExtendedPostgresDriver, HasDatabaseConfig, TableHolder}
import com.fan.impactech.client.mapper.ClientMapper
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}
import slick.basic.DatabaseConfig

import scala.concurrent.{ExecutionContext, Future}

class ClientDao (implicit val injector: Injector) extends Injectable
  with GenericDao[ClientDTO, Done]
  with HasDatabaseConfig[ExtendedPostgresDriver]
  with TableHolder
  with LazyLogging {
  import profile.api._

  private implicit val ec: ExecutionContext = inject[ExecutionContext]('executionContext)
  override protected val dbConfig: DatabaseConfig[ExtendedPostgresDriver] = DatabaseConfig.forConfig[ExtendedPostgresDriver]("database")
  private val query = TableQuery[ClientTable]
  private[this] val insertClientQuery = query returning query.map(_.clientId) into ((d, clientId) => d.copy(clientId = clientId))

  override def exists (id: String): Future[Boolean] = {
    db.run(query.filter(client => client.clientId === id).exists.result)
  }

  override def get (ids: Seq[String]): Future[Seq[ClientDTO]] = {
    db.run(query.filter(_.clientId inSet Set(ids: _*)).result.map(_.map(ClientMapper.apply)))
  }

  override def findAll (): Future[Seq[ClientDTO]] = {
    db.run(query.result.map(_.map(_.toClientDTO)))
  }

  override def put (entity: ClientDTO): Future[Done] = {
    val insertClient = insertClientQuery.insertOrUpdate(ClientMapper(entity))

    db.run(insertClient.transactionally).map(_ => Done)
  }

  override def remove (clientId: String): Future[Boolean] = {
    db.run(query.filter(_.clientId === clientId).delete).map(count ⇒ count > 0)
  }
}
