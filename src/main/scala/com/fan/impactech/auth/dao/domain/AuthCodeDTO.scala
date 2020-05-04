package com.fan.impactech.auth.dao.domain

import java.time.Instant

case class AuthCodeDTO (clientId: String,
                        callbackUrl: String,
                        code: String,
                        userName: String,
                        created: Instant,
                        expireAt: Instant)
