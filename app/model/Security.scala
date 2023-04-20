package model

import java.util.{UUID, Optional}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.compat.java8.FutureConverters._
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import play.api.mvc._
import org.pac4j.core.config.Config
import org.pac4j.core.profile.UserProfile
import org.pac4j.play.PlayWebContext
import org.pac4j.play.context.PlayFrameworkParameters
import org.pac4j.play.result.PlayWebContextResultHolder
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.context.CallContext
import org.pac4j.core.credentials.Credentials
import org.pac4j.core.credentials.UsernamePasswordCredentials
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.service.InMemoryProfileService
import org.pac4j.core.credentials.password.JBCryptPasswordEncoder
import org.pac4j.core.exception.AccountNotFoundException
import org.pac4j.core.exception.MultipleAccountsFoundException
import org.pac4j.core.exception.BadCredentialsException
import org.pac4j.core.exception.TechnicalException
import org.pac4j.core.util.Pac4jConstants
import org.pac4j.core.util.serializer.JsonSerializer
import org.pac4j.core.profile.definition.CommonProfileDefinition
import org.pac4j.core.credentials.password.PasswordEncoder
import model._
import model.auth._
import org.pac4j.http.client.indirect.FormClient

abstract class Security[P <: CommonProfile](val controllerComponents: SecurityComponents) extends BaseController {

  type AuthenticatedRequest[A] = model.AuthenticatedRequest[P, A]

  protected val parser: BodyParsers.Default        = controllerComponents.parser
  protected val config: Config                     = controllerComponents.config
  protected val executionContext: ExecutionContext = controllerComponents.executionContext

  protected def profiles[A](implicit request: AuthenticatedRequest[A]): List[P] = request.profiles

  def secureAsync(block: DashRequest[Ctx, AnyContent] => Future[Result]): Action[AnyContent] =
    Secure.async { x => dashRequest(x).flatMap(block)(controllerComponents.executionContext) }

  def secure(block: DashRequest[Ctx, AnyContent] => Result): Action[AnyContent] =
    Secure.async { x => dashRequest(x).map(block)(controllerComponents.executionContext) }

  private def dashRequest[A](s: AuthenticatedRequest[A]): Future[DashRequest[Ctx, A]] =
    s.profiles.headOption match {
      case Some(p) =>
        // Note: Applicative
        Future.successful(DashRequest(Ctx(p.getId(), p.getDisplayName()), UUID.randomUUID(), s))

      case None => Future.failed(new RuntimeException("Expected profile"))
    }

  protected def Secure: SecureAction[P, AnyContent, AuthenticatedRequest] =
    SecureAction[P, AnyContent, AuthenticatedRequest](
      Security.defaultClients,
      parser,
      config,
      None
    )(executionContext)

  protected def SecureWithRoles(rgs: RoleGroups): SecureAction[P, AnyContent, AuthenticatedRequest] =
    SecureAction[P, AnyContent, AuthenticatedRequest](
      Security.defaultClients,
      parser,
      config,
      Some(rgs)
    )(executionContext)

  //

  def formClient: Option[FormClient] =
    config.getClients().findClient("FormClient").toScala match {
      case Some(fc: FormClient) => Option(fc)
      case _                    => None
    }

}

final case class Ctx(userId: String, displayName: String)

final case class DashRequest[I, B](
  identity: I,
  uuid: UUID,
  request: Request[B]
) extends WrappedRequest(request)

object Security {

  val defaultClients: List[Client] = List(FormClient)

  sealed abstract class Client(val name: String)
  object FormClient extends Client("FormClient")
}

final case class SecureAction[
  P <: CommonProfile,
  ContentType,
  R[X] >: AuthenticatedRequest[P, X] <: Request[X]
](
  securityClients: List[Security.Client],
  parser: BodyParser[ContentType],
  config: Config,
  roleGroups: Option[RoleGroups]
)(implicit ec: ExecutionContext)
  extends ActionBuilder[R, ContentType] {
  protected val executionContext: ExecutionContext = ec

  def apply[A](action: Action[A]): Action[A] =
    copy(parser = action.parser).async(action.parser)(r => action.apply(r))

  def invokeBlock[A](r: Request[A], block: R[A] => Future[Result]): Future[Result] = {

    val request      = roleGroups.fold(r)(r.addAttr(RoleGroupsAuthorizer.roleGroupsKey, _))
    val authorizers  = roleGroups.fold("")(_ => RoleGroupsAuthorizer.authorizerName)
    val clients      = securityClients.map(_.name).mkString(",")
    val matchers     = ""
    val secureAction = new org.pac4j.play.java.SecureAction(config)
    val parameters   = new PlayFrameworkParameters(request)

    secureAction
      .call(parameters, clients, authorizers, matchers)
      .toScala
      .flatMap[Result] {

        case h: PlayWebContextResultHolder =>
          val webContext     = h.getPlayWebContext
          val sessionStore   = config.getSessionStoreFactory.newSessionStore(parameters)
          val profileManager = config.getProfileManagerFactory.apply(webContext, sessionStore)
          val profiles       = profileManager.getProfiles().asScala.toList.asInstanceOf[List[P]]
          val sRequest       = webContext.supplementRequest(r.asJava).asScala.asInstanceOf[Request[A]]

          block(AuthenticatedRequest(profiles, sRequest))

        case r => Future.successful(r.asScala)
      }
  }
}

final case class AuthenticatedRequest[P <: CommonProfile, A](
  profiles: List[P],
  request: Request[A]
) extends WrappedRequest[A](request)

trait SecurityComponents extends ControllerComponents {

  def components: ControllerComponents
  def config: Config
  def parser: BodyParsers.Default

  @inline def actionBuilder    = components.actionBuilder
  @inline def parsers          = components.parsers
  @inline def messagesApi      = components.messagesApi
  @inline def langs            = components.langs
  @inline def fileMimeTypes    = components.fileMimeTypes
  @inline def executionContext = components.executionContext
}

@Singleton
class DefaultSecurityComponents @Inject() (
  val config: Config,
  val parser: BodyParsers.Default,
  val components: ControllerComponents
) extends SecurityComponents

class ProfileAuth(entries: List[ProfileAuth.Entry], encoder: PasswordEncoder)
  extends InMemoryProfileService[CommonProfile](_ => new CommonProfile()) {
  setPasswordEncoder(encoder)
  entries.foreach(e => create(e.profile, e.encodedPassword))
}

object ProfileAuth {

  val entries = List(
    Entry(
      "ahj",
      "Alex Henning Johannessen",
      List(Admin, ClerkL1),
      "$2a$10$2n.zlTGCq1DFDhW/ZfKFS.Wnh/q.KwE5epUCvKLoytpxoloG/QuJe"
    ),
    Entry(
      "clerk",
      "Demo User",
      List(ClerkL1, Supervisor),
      "$2a$10$6L7t0I.jXm/pmrVcbuaB/.cEq.SsxTh8oNICnvw1P7e9XDhUZDL3i"
    ),
    Entry(
      "ohnoes",
      "No Roles",
      List.empty,
      "$2a$10$dwGbpSbrtpuwqZGZLHkN.es6F8snvrNVwad9neRq9Q7NaqdbCVfiG"
    )
  )

  def apply(): ProfileAuth =
    new ProfileAuth(entries, PassthroughEncoder())

  ///

  final case class Entry(
    profile: CommonProfile,
    encodedPassword: String
  )

  object Entry {

    def apply(username: String, displayName: String, roles: List[Role], encodedPassword: String): Entry =
      Entry(Staff(username, displayName, roles), encodedPassword)

  }

}

object PassthroughEncoder {

  def apply(): PasswordEncoder = new PasswordEncoder {
    def encode(encodedPassword: String): String = encodedPassword
    def matches(plainPassword: String, encodedPassword: String): Boolean =
      org.mindrot.jbcrypt.BCrypt.checkpw(plainPassword, encodedPassword)

  }
}
