package com.fan.impactech.client.dao.domain

import io.circe.Encoder

sealed abstract class ClientState(val id: String) {
  override def toString: String = id
}
object ClientState {
  case object Active extends ClientState("active")
  case object Inactive extends ClientState("inactive")

  val all: Set[ClientState] = Set(Active, Inactive)

  def of(s: String): ClientState = all.find(_.id == s).getOrElse(throw new Exception(s"Not found Client state for provided string: $s"))

  implicit val encode: Encoder[ClientState] = Encoder.encodeString.contramap[ClientState](_.toString)
}