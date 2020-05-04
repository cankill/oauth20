package com.fan.impactech.token.dao.domain

import java.time.Instant

case class TokenDTO (clientId: String,
                     userName: String,
                     callbackUrl: String,
                     accessToken: String,
                     refreshToken: String,
                     created: Instant,
                     expireAt: Instant)
