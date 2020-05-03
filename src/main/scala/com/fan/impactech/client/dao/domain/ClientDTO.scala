package com.fan.impactech.client.dao.domain

import java.time.Instant

case class ClientDTO (clientId: String,
                      secretId: String,
                      callbackUrl: String,
                      state: ClientState,
                      created: Instant,
                      modified: Option[Instant])
