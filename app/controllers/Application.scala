package controllers

import scala.jdk.CollectionConverters._
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc._
import org.pac4j.core.client.IndirectClient
import org.pac4j.core.context.CallContext
import org.pac4j.core.exception.http.WithLocationAction
import org.pac4j.core.profile._
import org.pac4j.core.util.{CommonHelper, Pac4jConstants}
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.play.PlayWebContext
import org.pac4j.play.context.PlayFrameworkParameters
import org.pac4j.core.engine.DefaultCallbackLogic
import org.pac4j.play.scala._
import model.auth._

class Application @Inject() (val sc: model.SecurityComponents) extends model.Security[CommonProfile](sc) {

  def index = Secure { implicit request =>
    val parameters   = new PlayFrameworkParameters(request)
    val webContext   = config.getWebContextFactory.newContext(parameters).asInstanceOf[PlayWebContext]
    val sessionStore = config.getSessionStoreFactory.newSessionStore(parameters)
    val sessionId    = sessionStore.getSessionId(webContext, false).orElse("nosession")
    val csrfToken    = webContext.getRequestAttribute(Pac4jConstants.CSRF_TOKEN).orElse(null).asInstanceOf[String]
    webContext.supplementResponse(Ok(views.html.index(profiles, csrfToken, sessionId)))
  }

  def csrfIndex      = Secure { implicit request => Ok(views.html.csrf(profiles)) }
  def protectedIndex = secure { implicit request => Ok(views.html.protectedIndex(List.empty[UserProfile])) }

  def protectedCustomIndex = SecureWithRoles(List(List(Admin), List(Supervisor))) { implicit request =>
    Ok(views.html.protectedIndex(profiles))
  }

  def formIndex = Secure { implicit request => Ok(views.html.protectedIndex(profiles)) }

  // Setting the isAjax parameter is no longer necessary as AJAX requests are automatically detected:
  // a 401 error response will be returned instead of a redirection to the login url.
  def formIndexJson = Secure { implicit request =>
    val content = views.html.protectedIndex.render(profiles, request)
    val json    = Json.obj("content" -> content.toString())
    Ok(json).as("application/json")
  }

  def basicauthIndex = Secure { implicit request => Ok(views.html.protectedIndex(profiles)) }

  def loginForm = Action { request =>
    formClient match {
      case None     => InternalServerError
      case Some(fc) => Ok(views.html.loginForm.render(fc.getCallbackUrl, request.queryString))
    }

  }

  def forceLogin = Action { request =>
    val parameters   = new PlayFrameworkParameters(request)
    val webContext   = config.getWebContextFactory.newContext(parameters).asInstanceOf[PlayWebContext]
    val sessionStore = config.getSessionStoreFactory.newSessionStore(parameters)
    val client = config.getClients
      .findClient(webContext.getRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER).get)
      .get
      .asInstanceOf[IndirectClient]
    val location = client
      .getRedirectionAction(new CallContext(webContext, sessionStore, config.getProfileManagerFactory))
      .get
      .asInstanceOf[WithLocationAction]
      .getLocation
    webContext.supplementResponse(Redirect(location))
  }
}
