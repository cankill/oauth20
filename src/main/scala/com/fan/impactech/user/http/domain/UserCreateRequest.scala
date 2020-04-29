package com.fan.impactech.user.http.domain

case class UserCreateRequest(login: String,
                             password: String,
                             user_name: String)
