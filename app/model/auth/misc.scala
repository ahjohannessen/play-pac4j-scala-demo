package model
package auth

import java.{util => ju}
import scala.concurrent.Future
import scala.collection.immutable
import scala.jdk.CollectionConverters._
import play.api.mvc.Request
import play.api.libs.typedmap.TypedKey
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.authorization.authorizer.ProfileAuthorizer
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.profile.UserProfile
import org.pac4j.core.util.Pac4jConstants
import org.pac4j.core.profile.definition.CommonProfileDefinition
import org.pac4j.play.PlayWebContext
import model.auth._

abstract class Role(
  val name: String
)

case object Admin      extends Role("admin")
case object Supervisor extends Role("supervisor")
case object ClerkL1    extends Role("clerk_level1")

///

object Staff {

  def apply(username: String, displayName: String, roles: List[Role]): CommonProfile = {
    val s = new CommonProfile()
    s.setId(username)
    s.addAttribute(Pac4jConstants.USERNAME, username)
    s.addRoles(roles.map(_.name).asJavaCollection)
    s.addAttribute(CommonProfileDefinition.DISPLAY_NAME, displayName)
    s
  }

}

///

object RoleGroupsAuthorizer extends ProfileAuthorizer {

  val authorizerName: String              = "RoleGroupsAuthorizer"
  val roleGroupsKey: TypedKey[RoleGroups] = TypedKey[RoleGroups]("RoleGroups")

  def isAuthorized(user: UserProfile, groups: RoleGroups): Boolean = {
    user.getRoles.asScala.contains(Admin.name) || groups.foldLeft(false) { (a, rg) =>
      a || rg.nonEmpty && rg.map(_.name).intersect(user.getRoles.asScala.toList).size == rg.size
    }
  }

  override def isProfileAuthorized(context: WebContext, sessionStore: SessionStore, profile: UserProfile): Boolean = {

    val rgs: Option[RoleGroups] = context match {
      case p: PlayWebContext => p.getNativeScalaRequest.attrs.get(roleGroupsKey)
      case _                 => None
    }

    rgs.exists(isAuthorized(profile, _))
  }

  override def isAuthorized(context: WebContext, sessionStore: SessionStore, profiles: ju.List[UserProfile]): Boolean =
    isAnyAuthorized(context, sessionStore, profiles)

}
