package com.fan.impactech.user.mapper

import java.time.Instant

import com.fan.impactech.client.dao.domain
import com.fan.impactech.db.UserEntry
import com.fan.impactech.user.dao.domain.UserDTO
import com.fan.impactech.user.http.domain.UserCreateRequest

object UserMapper {
  def apply(userDTO: UserDTO): UserEntry = UserEntry(userDTO.userName,
                                                     userDTO.password,
                                                     userDTO.name,
                                                     userDTO.created,
                                                     userDTO.modified)

  def apply(userEntry: UserEntry): UserDTO = UserDTO(userEntry.userName,
                                                     userEntry.password,
                                                     userEntry.name,
                                                     userEntry.created,
                                                     userEntry.modified)

  def apply(userCreateRequest: UserCreateRequest): UserDTO = UserDTO(userCreateRequest.user_name,
                                                                     userCreateRequest.password,
                                                                     userCreateRequest.name,
                                                                     Instant.now,
                                                                     None)
}