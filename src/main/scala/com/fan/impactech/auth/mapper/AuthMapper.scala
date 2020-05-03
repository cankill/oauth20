package com.fan.impactech.auth.mapper

import com.fan.impactech.auth.dao.domain.AuthCodeDTO
import com.fan.impactech.db.AuthCodeEntry

object AuthMapper {
  def apply(authDTO: AuthCodeDTO): AuthCodeEntry = AuthCodeEntry(authDTO.clientId,
                                                                 authDTO.callbackUrl,
                                                                 authDTO.code,
                                                                 authDTO.userName,
                                                                 authDTO.created,
                                                                 authDTO.expireAt,
                                                                 authDTO.modified)

  def apply(authDTO: AuthCodeEntry): AuthCodeDTO = AuthCodeDTO(authDTO.clientId,
                                                               authDTO.callbackUrl,
                                                               authDTO.code,
                                                               authDTO.userName,
                                                               authDTO.created,
                                                               authDTO.expireAt,
                                                               authDTO.modified)
}
