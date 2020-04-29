package com.fan.impactech.db

import slick.basic.{BasicProfile, DatabaseConfig}

trait HasDatabaseConfig[T <: BasicProfile] {
  protected val dbConfig: DatabaseConfig[T]
  protected final lazy val profile: T = dbConfig.profile
  protected final def db: T#Backend#Database = dbConfig.db
}