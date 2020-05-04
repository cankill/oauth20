package com.fan.impactech.token.http.domain

import java.time.Instant

case class TokenResponse (access_token: String,
                          refresh_token: String,
                          token_type: String,
                          expires_at: Instant)
