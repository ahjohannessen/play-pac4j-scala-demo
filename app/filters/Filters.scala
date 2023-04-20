package filters

import javax.inject.Inject
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.csp.CSPFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import org.pac4j.play.filters.SecurityFilter

class Filters @Inject() (csrf: CSRFFilter, shf: SecurityHeadersFilter, csp: CSPFilter, pac4jsf: SecurityFilter)
  extends HttpFilters {
  def filters: Seq[EssentialFilter] = Seq(pac4jsf, csrf, shf, csp)
}
