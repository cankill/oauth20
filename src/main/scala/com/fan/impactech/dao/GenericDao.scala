package com.fan.impactech.dao

import scala.concurrent.Future

trait GenericDao[T,R] {
  def exists (id: String): Future[Boolean]
  def findAll (): Future[Seq[T]]
  def get (ids: Seq[String]): Future[Seq[T]]
  def put (entity: T): Future[R]
  def remove (clientId: String): Future[Boolean]
}