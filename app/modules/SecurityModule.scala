package modules

import java.io.File
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import com.google.inject.{AbstractModule, Provides, Singleton}
import org.pac4j.core.authorization.authorizer.{Authorizer, RequireAnyRoleAuthorizer}
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.credentials.password.JBCryptPasswordEncoder
import org.pac4j.core.client.Clients
import org.pac4j.core.client.direct.AnonymousClient
import org.pac4j.core.config.Config
import org.pac4j.core.context.FrameworkParameters
import org.pac4j.core.context.session.{SessionStore, SessionStoreFactory}
import org.pac4j.core.matching.matcher.PathMatcher
import org.pac4j.core.profile.CommonProfile
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.play.scala.{DefaultSecurityComponents, Pac4jScalaTemplateHelper, SecurityComponents}
import org.pac4j.play.store.{PlayCacheSessionStore, PlayCookieSessionStore, ShiroAesDataEncrypter}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}
import controllers.CustomHttpActionAdapter
import model._
import model.auth._

/** Guice DI module to be included in application.conf
  */
class SecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  val baseUrl     = configuration.get[String]("baseUrl")
  val defaultUrl  = baseUrl + controllers.routes.Application.index().url
  val loginUrl    = baseUrl + controllers.routes.Application.loginForm().url
  val callbackUrl = baseUrl + org.pac4j.play.routes.CallbackController.callback().url

  println(defaultUrl)
  println(loginUrl)
  println(callbackUrl)

  override def configure(): Unit = {

    val sKey             = configuration.get[String]("play.http.secret.key").substring(0, 16)
    val dataEncrypter    = new ShiroAesDataEncrypter(sKey.getBytes(StandardCharsets.UTF_8))
    val playSessionStore = new PlayCookieSessionStore(dataEncrypter)

    bind(classOf[SessionStore]).toInstance(playSessionStore)
    bind(classOf[model.SecurityComponents]).to(classOf[model.DefaultSecurityComponents])
    bind(classOf[Pac4jScalaTemplateHelper[CommonProfile]])

    // callback
    val callbackController = new CallbackController()
    callbackController.setDefaultUrl(defaultUrl)
    callbackController.setRenewSession(false)
    bind(classOf[CallbackController]).toInstance(callbackController)

    // logout
    val logoutController = new LogoutController()
    logoutController.setDefaultUrl(defaultUrl)
    logoutController.setDestroySession(true)
    bind(classOf[LogoutController]).toInstance(logoutController)
  }

  @Provides
  @Singleton
  def providesProfileAuthenticator: Authenticator =
    ProfileAuth()

  @Provides
  def provideFormClient(authenticator: Authenticator): FormClient =
    new FormClient(loginUrl, authenticator)

  @Provides
  def provideConfig(formClient: FormClient, sessionStore: SessionStore): Config = {
    val clients     = new Clients(callbackUrl, formClient)
    val authorizers = Map[String, Authorizer](RoleGroupsAuthorizer.authorizerName -> RoleGroupsAuthorizer)
    val config      = new Config(clients, authorizers.asJava)

    config.setSessionStoreFactory(new SessionStoreFactory {
      def newSessionStore(parameters: FrameworkParameters): SessionStore = sessionStore
    });
    config.setHttpActionAdapter(new CustomHttpActionAdapter())
    config
  }
}
