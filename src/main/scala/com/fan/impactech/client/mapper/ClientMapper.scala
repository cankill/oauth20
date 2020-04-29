package com.fan.impactech.client.mapper

import java.time.Instant

import com.fan.impactech.client.dao.{ClientEntry, domain}
import com.fan.impactech.client.dao.domain.{ClientDTO, ClientState}
import com.fan.impactech.client.http.domain.ClientCreateRequest

object ClientMapper {
  def apply(clientDTO: ClientDTO): ClientEntry = ClientEntry(clientDTO.clientId,
                                                             clientDTO.applicationId,
                                                             clientDTO.secretId,
                                                             clientDTO.callbackUrl,
                                                             clientDTO.state.id,
                                                             clientDTO.created,
                                                             clientDTO.modified)

  def apply(clientEntry: ClientEntry): ClientDTO = ClientDTO(clientEntry.clientId,
                                                             clientEntry.applicationId,
                                                             clientEntry.secretId,
                                                             clientEntry.callbackUrl,
                                                             ClientState.of(clientEntry.state),
                                                             clientEntry.created,
                                                             clientEntry.modified)

  def apply(clientRequest: ClientCreateRequest): ClientDTO = ClientDTO(clientRequest.client_id,
                                                                       clientRequest.application_id,
                                                                       clientRequest.secret_id,
                                                                       clientRequest.callback_url,
                                                                       ClientState.Active,
                                                                       Instant.now,
                                                                       None)

}