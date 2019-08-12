package uk.gov.hmrc.cgtpropertydisposals.connectors

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

trait DesConnector {

  val config: ServicesConfig

  val bearerToken: String = config.getString("des.bearer-token")

  val environment: String = config.getString("des.environment")

  val headers: Map[String, String] = Map(
    "Authorization" -> s"Bearer $bearerToken",
    "Environment" -> environment
  )

}
