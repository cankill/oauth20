package com.fan.impactech.db

import java.sql.{Connection, DriverManager}

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor

object DatabasePatcher extends LazyLogging {
  private val changeLogFile = "patches/changelog.yaml"

  def patch(implicit config: Config): Unit = {
    val dbConnection = getConnection
    val liquibase = createLiquibase(dbConnection)
    try {
      liquibase.update("MIGRATION")
    } catch {
      case e: Throwable => throw e
    } finally {
      liquibase.forceReleaseLocks()
      dbConnection.rollback()
      dbConnection.close()
    }
  }

  private def getConnection(implicit config: Config) = {
    val driver: String = config.getString("database.db.driver")
    Class.forName(driver)

    val url: String = config.getString("database.db.url")
    val user: String = config.getString("database.db.user")
    val password: String = config.getString("database.db.password")
    DriverManager.getConnection(url, user, password)
  }

  private def createLiquibase(dbConnection: Connection): Liquibase = {
    val database = DatabaseFactory.getInstance.findCorrectDatabaseImplementation(new JdbcConnection(dbConnection))
    val resourceAccessor = new ClassLoaderResourceAccessor(getClass.getClassLoader)
    new Liquibase(changeLogFile, resourceAccessor, database)
  }
}
