package com.fan.impactech.user.dao.domain

import java.time.Instant

case class UserDTO (login: String,
                    password: String,
                    userName: String,
                    created: Instant,
                    modified: Option[Instant])
