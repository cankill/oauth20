package com.fan.impactech.user.dao

import java.time.Instant

import com.fan.impactech.db.{ExtendedPostgresDriver, HasDatabaseConfig}
import com.fan.impactech.user.dao.domain.UserDTO

final case class UserEntry (login: String,
                            password: String,
                            userName: String,
                            created: Instant,
                            modified: Option[Instant]) {
  def toUserDTO: UserDTO = UserDTO(login, password, userName, created, modified)
}

trait UserTableHolder { self: HasDatabaseConfig[ExtendedPostgresDriver] =>
  import profile.api._

  class UserTable(tag: Tag) extends Table[UserEntry](tag, "user") {
    def login = column[String]("login", O.PrimaryKey)
    def password = column[String]("password")
    def userName = column[String]("user_name")
    def created = column[Instant]("created")
    def modified = column[Option[Instant]]("modified")

    override def * = (login, password, userName, created, modified) <> (UserEntry.tupled, UserEntry.unapply)
  }
}
