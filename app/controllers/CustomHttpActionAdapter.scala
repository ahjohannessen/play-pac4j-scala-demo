package controllers

import org.pac4j.core.context.{HttpConstants, WebContext}
import org.pac4j.core.exception.http.HttpAction
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import play.mvc._
import Results.{forbidden, unauthorized}
import HttpConstants.{UNAUTHORIZED, FORBIDDEN, HTML_CONTENT_TYPE}
import views.html.{error403, error401}

class CustomHttpActionAdapter extends PlayHttpActionAdapter {
  override def adapt(action: HttpAction, context: WebContext): Result =
    (Option(action), context) match {
      case (Some(a: HttpAction), pwc: PlayWebContext) =>
        if (a.getCode == UNAUTHORIZED)
          pwc.supplementResponse(unauthorized(error401.render().toString()).as(HTML_CONTENT_TYPE))
        else if (a.getCode == FORBIDDEN)
          pwc.supplementResponse(forbidden(error403.render().toString()).as(HTML_CONTENT_TYPE))
        else
          super.adapt(a, pwc)
      case _ =>
        super.adapt(action, context)
    }
}
