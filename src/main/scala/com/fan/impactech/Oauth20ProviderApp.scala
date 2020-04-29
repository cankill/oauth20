package com.fan.impactech

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import java.sql.SQLException

import akka.{Done, actor}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, ActorRef => TypedActorRef}
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.{ConnectionContext, Http}
import com.fan.impactech.client.dao.ClientDao
import com.fan.impactech.client.dao.domain.ClientDTO
import com.fan.impactech.client.http.ClientRestService
import com.fan.impactech.dao.GenericDao
import com.fan.impactech.db.DatabasePatcher
import com.fan.impactech.client.http.validate.ClientValidator
import com.fan.impactech.client.repo.ClientRepository
import com.fan.impactech.user.dao.UserDao
import com.fan.impactech.user.dao.domain.UserDTO
import com.fan.impactech.user.http.UserRestService
import com.fan.impactech.user.http.validate.UserValidator
import com.fan.impactech.user.repo.UserRepository
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.postgresql.util.PSQLException
import scaldi.Module
import scaldi.akka.AkkaInjectable

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object Oauth20ProviderApp extends App with AkkaInjectable with LazyLogging {
  private val backOff = 30 seconds
  private val sqlErrors = Seq("08", "53")

  private implicit val system: actor.ActorSystem = akka.actor.ActorSystem("Oauth20System")
  private implicit val ec: ExecutionContext = system.dispatcher
  private val typedSystem: ActorSystem[Nothing] = system.toTyped

  private lazy val config: Config = ConfigFactory.load()
  config.resolve()

  private implicit val appModule: Module = new Module {
    bind[Config] to config

    bind[ActorSystem[Nothing]] to typedSystem destroyWith (_.terminate())
    binding identifiedBy 'executionContext to ec

    bind[GenericDao[ClientDTO, Done]] to new ClientDao()
    bind[GenericDao[UserDTO, Done]] to new UserDao()

    bind[TypedActorRef[ClientRepository.Protocol]] to
      typedSystem.systemActorOf(ClientRepository.behaviour(config, inject[GenericDao[ClientDTO, Done]]), ClientRepository.name)

    bind[TypedActorRef[ClientValidator.Protocol]] to
      typedSystem.systemActorOf(ClientValidator.behaviour(config), ClientValidator.name)

    bind[TypedActorRef[UserRepository.Protocol]] to
      typedSystem.systemActorOf(UserRepository.behaviour(config, inject[GenericDao[UserDTO, Done]]), UserRepository.name)

    bind[TypedActorRef[UserValidator.Protocol]] to
      typedSystem.systemActorOf(UserValidator.behaviour(config), UserValidator.name)

    bind[ClientRestService] to new ClientRestService()
    bind[UserRestService] to new UserRestService()
  }

  patchDataBase {
    val route = {
      val httpUserService: UserRestService = inject[UserRestService]
      val userServiceLogRequest = DebuggingDirectives.logRequest("UserManagement", Logging.DebugLevel)(httpUserService.userManagementRoute)
      val userServiceRoute = DebuggingDirectives.logResult("UserManagement", Logging.DebugLevel)(userServiceLogRequest)

      val httpClientService: ClientRestService = inject[ClientRestService]
      val clientServiceLogRequest = DebuggingDirectives.logRequest("ClientManagement", Logging.DebugLevel)(httpClientService.clientsManagementRoute)
      val clientServiceRoute = DebuggingDirectives.logResult("ClientManagement", Logging.DebugLevel)(clientServiceLogRequest)

      userServiceRoute ~ clientServiceRoute
    }

    val httpsContext = makeHttpsContext()
//    val httpsContext = ConnectionContext.noEncryption()
    Http()
      .bindAndHandle(route, config.getString("http.host"), config.getInt("http.port"), httpsContext)
      .map(b => logger.info(s"API Bound to ${b.localAddress}"))
      .onComplete {
        case Failure(e) =>
          logger.error(s"Could not bind to interface", e)
          appModule.destroy()
        case Success(_) => ()
      }
  }

  @tailrec
  private[this] def patchDataBase(onSuccess: => Unit): Unit = {
    Try(DatabasePatcher.patch(config)) match {
      case Failure(ex : SQLException) if sqlErrors.contains(ex.getSQLState.take(2)) =>
        logger.error(s"Failed to patch DataBase. Retry after ${backOff.toSeconds} seconds", ex)
        Thread.sleep(backOff.toMillis)
        patchDataBase(onSuccess)

      case Failure(ex : PSQLException) if ex.getServerErrorMessage.getMessage == "FATAL: the database system is starting up" =>
        logger.error(s"Failed to patch DataBase. Retry after ${backOff.toSeconds} seconds", ex)
        Thread.sleep(backOff.toMillis)
        patchDataBase(onSuccess)

      case Failure(ex) =>
        logger.error("Failed to patch DataBase.", ex)
        appModule.destroy()

      case Success(_) => onSuccess
    }
  }

  private def makeHttpsContext(): ConnectionContext = {
    val keystorePath = sys.props.get("keystore.path").getOrElse(config.getString("keystore.path"))
    val password = sys.props.get("keystore.password").getOrElse(config.getString("keystore.password")).toCharArray

    val javaKeyStore = KeyStore.getInstance("jks")
    javaKeyStore.load(new FileInputStream(keystorePath), password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(javaKeyStore, password)

    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(javaKeyStore)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

    ConnectionContext.https(sslContext)
  }
}
