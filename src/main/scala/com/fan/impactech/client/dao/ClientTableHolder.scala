package com.fan.impactech.client.dao

import java.time.Instant

import com.fan.impactech.client.dao.domain.{ClientDTO, ClientState}
import com.fan.impactech.db.{ExtendedPostgresDriver, HasDatabaseConfig}

final case class ClientEntry (clientId: String,
                              applicationId: String,
                              secretId: String,
                              callbackUrl: String,
                              state: String,
                              created: Instant,
                              modified: Option[Instant]) {
  def toClientDTO: ClientDTO = ClientDTO(clientId, applicationId, secretId, callbackUrl, ClientState.of(state), created, modified)
}

trait ClientTableHolder { self: HasDatabaseConfig[ExtendedPostgresDriver] =>
  import profile.api._

  class ClientTable(tag: Tag) extends Table[ClientEntry](tag, "client") {
    def clientId = column[String]("client_id", O.PrimaryKey)
    def applicationId = column[String]("application_id")
    def secretId = column[String]("secret_id")
    def callbackUrl = column[String]("callback_url")
    def state = column[String]("state")
    def created = column[Instant]("created")
    def modified = column[Option[Instant]]("modified")

    override def * = (clientId, applicationId, secretId, callbackUrl, state, created, modified) <> (ClientEntry.tupled, ClientEntry.unapply)
  }
}
