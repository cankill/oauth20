package com.fan.impactech.client.http.domain

case class ClientCreateRequest(client_id: String,
                               application_id: String,
                               secret_id: String,
                               callback_url: String)
