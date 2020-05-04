package com.fan.impactech.db

import java.time.Instant

import com.fan.impactech.auth.dao.domain.AuthCodeDTO
import com.fan.impactech.client.dao.domain.{ClientDTO, ClientState}
import com.fan.impactech.token.dao.domain.TokenDTO
import com.fan.impactech.user.dao.domain.UserDTO

final case class ClientEntry (clientId: String,
                              secretId: String,
                              callbackUrl: String,
                              state: String,
                              created: Instant,
                              modified: Option[Instant]) {
  def toClientDTO: ClientDTO = ClientDTO(clientId, secretId, callbackUrl, ClientState.of(state), created, modified)
}

final case class UserEntry (userName: String,
                            password: String,
                            name: String,
                            created: Instant,
                            modified: Option[Instant]) {
  def toUserDTO: UserDTO = UserDTO(userName, password, name, created, modified)
}

final case class AuthCodeEntry (clientId: String,
                                callbackUrl: String,
                                code: String,
                                userName: String,
                                used: Boolean,
                                created: Instant,
                                expireAt: Instant) {
  def toAuthCodeDTO: AuthCodeDTO = AuthCodeDTO(clientId, callbackUrl, code, userName, created, expireAt)
}

final case class TokenEntry (clientId: String,
                             userName: String,
                             callbackUrl: String,
                             accessToken: String,
                             refreshToken: String,
                             revoked: Boolean,
                             created: Instant,
                             expireAt: Instant) {
  def toTokenDTO: TokenDTO = TokenDTO(clientId, userName, callbackUrl, accessToken, refreshToken, created, expireAt)
}

trait TableHolder { self: HasDatabaseConfig[ExtendedPostgresDriver] =>
  import profile.api._

  class ClientTable(tag: Tag) extends Table[ClientEntry](tag, "client") {
    def clientId = column[String]("client_id", O.PrimaryKey)
    def secretId = column[String]("secret_id")
    def callbackUrl = column[String]("callback_url")
    def state = column[String]("state")
    def created = column[Instant]("created")
    def modified = column[Option[Instant]]("modified")

    override def * = (clientId, secretId, callbackUrl, state, created, modified) <> (ClientEntry.tupled, ClientEntry.unapply)
  }

  class UserTable(tag: Tag) extends Table[UserEntry](tag, "user") {
    def userName = column[String]("user_name", O.PrimaryKey)
    def password = column[String]("password")
    def name = column[String]("name")
    def created = column[Instant]("created")
    def modified = column[Option[Instant]]("modified")

    override def * = (userName, password, name, created, modified) <> (UserEntry.tupled, UserEntry.unapply)
  }

  val clients = TableQuery[ClientTable]
  val users = TableQuery[UserTable]

  class AuthorizationCodeTable(tag: Tag) extends Table[AuthCodeEntry](tag, "authorization_code") {
    def clientId = column[String]("client_id")
    def callbackUrl = column[String]("callback_url")
    def code = column[String]("code")
    def userName = column[String]("user_name")
    def used = column[Boolean]("used")
    def created = column[Instant]("created")
    def expireAt = column[Instant]("expire_at")

    def idx = index("idx_client_id_callback_url_user_name", (clientId, callbackUrl, userName))
    def fkClientId = foreignKey("fk_client_id", clientId, clients)(_.clientId, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
    def fkUserId = foreignKey("fk_user_id", userName, users)(_.userName, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)

    override def * = (clientId, callbackUrl, code, userName, used, created, expireAt) <> (AuthCodeEntry.tupled, AuthCodeEntry.unapply)
  }

  val authCode = TableQuery[AuthorizationCodeTable]

  class TokenTable(tag: Tag) extends Table[TokenEntry](tag, "token") {
    def clientId = column[String]("client_id")
    def userName = column[String]("user_name")
    def callbackUrl = column[String]("callback_url")
    def accessToken = column[String]("access_token")
    def refreshToken = column[String]("refresh_token")
    def revoked = column[Boolean]("revoked")
    def created = column[Instant]("created")
    def expireAt = column[Instant]("expire_at")

    def idx = index("idx_client_id_callback_url_user_name", (clientId, userName, callbackUrl))
    def fkClientId = foreignKey("fk_client_id", clientId, clients)(_.clientId, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)
    def fkUserId = foreignKey("fk_user_id", userName, users)(_.userName, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)

    override def * = (clientId, userName, callbackUrl, accessToken, refreshToken, revoked, created, expireAt) <> (TokenEntry.tupled, TokenEntry.unapply)
  }

  val tokens = TableQuery[TokenTable]
}
