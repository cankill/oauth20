package com.fan.impactech.exampleresource.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.util.Timeout
import com.fan.impactech.token.mapper.TokenMapper
import com.fan.impactech.token.service.TokenService
import com.fan.impactech.token.service.TokenService._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.syntax._
import scaldi.{Injectable, Injector}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ExampleResourceRestService (implicit inject: Injector) extends Injectable with FailFastCirceSupport with LazyLogging {
  private implicit val executionContext: ExecutionContext = inject[ExecutionContext]('executionContext)
  private implicit val actorSystem: ActorSystem[Nothing] = inject[ActorSystem[Nothing]]

  val tokenService: TypedActorRef[TokenService.Protocol] = inject[TypedActorRef[TokenService.Protocol]]

  private val config: Config = inject[Config]
  private implicit val timeout: Timeout = 2.minutes

  def myAuth20Authenticator(credentials: Credentials): Future[Option[String]] = credentials match {
      case p @ Credentials.Provided(access_token) =>
        logger.debug(s"Got request to authenticate: $access_token")
        tokenService.ask[TokenResponseMessage](ref => Oauth20Authenticate(access_token, p, ref)).transform {
          case Success(Authenticated) =>
            logger.error(s"Authenticated")
            Success(Some(access_token))

          case Success(Rejected(error, reason)) =>
            logger.error(s"Rejected with code: $error and reason: $reason")
            Success(Option.empty[String])

          case Success(DbFailure(ex)) =>
            logger.error(s"Rejected. Db failure: $ex")
            Success(Option.empty[String])

          case Failure(ex) =>
            logger.error(s"Failed to Authenticate with exception: $ex")
            Success(Option.empty[String])
        }

      case _ => Future.successful(None)
  }

  def resourceRoute: Route =
    get {
        path("resource") {
          authenticateOAuth2Async(realm = "oauth20", myAuth20Authenticator) { _ =>
            logger.debug(s"Got request to get an example resource")
            complete(StatusCodes.OK, """{"access":"granted","resource":"ok"}""".asJson)
          }
      }
  }
}
