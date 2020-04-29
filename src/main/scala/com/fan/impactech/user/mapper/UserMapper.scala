package com.fan.impactech.user.mapper

import java.time.Instant

import com.fan.impactech.client.dao.{ClientEntry, domain}
import com.fan.impactech.client.dao.domain.{ClientDTO, ClientState}
import com.fan.impactech.client.http.domain.ClientCreateRequest
import com.fan.impactech.user.dao.UserEntry
import com.fan.impactech.user.dao.domain.UserDTO
import com.fan.impactech.user.http.domain.UserCreateRequest

object UserMapper {
  def apply(userDTO: UserDTO): UserEntry = UserEntry(userDTO.login,
                                                     userDTO.password,
                                                     userDTO.userName,
                                                     userDTO.created,
                                                     userDTO.modified)

  def apply(userEntry: UserEntry): UserDTO = UserDTO(userEntry.login,
                                                     userEntry.password,
                                                     userEntry.userName,
                                                     userEntry.created,
                                                     userEntry.modified)

  def apply(userCreateRequest: UserCreateRequest): UserDTO = UserDTO(userCreateRequest.login,
                                                                     userCreateRequest.password,
                                                                     userCreateRequest.user_name,
                                                                     Instant.now,
                                                                     None)
}