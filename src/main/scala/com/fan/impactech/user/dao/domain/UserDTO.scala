package com.fan.impactech.user.dao.domain

import java.time.Instant

case class UserDTO (userName: String,
                    password: String,
                    name: String,
                    created: Instant,
                    modified: Option[Instant])
