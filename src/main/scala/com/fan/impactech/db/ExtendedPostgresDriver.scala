package com.fan.impactech.db

import com.github.tminglei.slickpg.{ExPostgresProfile, PgArraySupport, PgCirceJsonSupport, PgDate2Support}
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities

trait ExtendedPostgresDriver extends ExPostgresProfile
  with PgArraySupport
  with PgDate2Support
  with PgCirceJsonSupport {

  override def pgjson: String = "jsonb"

  override protected def computeCapabilities: Set[Capability] = super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api = new API
    with ArrayImplicits
    with SimpleArrayPlainImplicits
    with DateTimeImplicits
    with CirceJsonPlainImplicits
}

object ExtendedPostgresDriver extends ExtendedPostgresDriver
