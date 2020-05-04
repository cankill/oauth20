package com.fan.impactech.token.mapper

import com.fan.impactech.db.TokenEntry
import com.fan.impactech.token.dao.domain.TokenDTO
import com.fan.impactech.token.http.domain.TokenResponse

object TokenMapper {
  def apply(tokenDTO: TokenDTO): TokenEntry = TokenEntry(tokenDTO.clientId,
                                                         tokenDTO.userName,
                                                         tokenDTO.callbackUrl,
                                                         tokenDTO.accessToken,
                                                         tokenDTO.refreshToken,
                                                         false,
                                                         tokenDTO.created,
                                                         tokenDTO.expireAt)

  def apply(tokenEntry: TokenEntry): TokenDTO = TokenDTO(tokenEntry.clientId,
                                                         tokenEntry.userName,
                                                         tokenEntry.callbackUrl,
                                                         tokenEntry.accessToken,
                                                         tokenEntry.refreshToken,
                                                         tokenEntry.created,
                                                         tokenEntry.expireAt)

  def toRepresentation(tokenDTO: TokenDTO): TokenResponse = TokenResponse(tokenDTO.accessToken,
                                                                          tokenDTO.refreshToken,
                                                                          "bearer",
                                                                          tokenDTO.expireAt)
}
