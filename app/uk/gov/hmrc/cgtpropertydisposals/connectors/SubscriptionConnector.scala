package uk.gov.hmrc.cgtpropertydisposals.connectors

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.{NINO, SubscriptionDetails}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

trait SubscriptionConnector {

  def subscribe(subscriptionDetails: SubscriptionDetails)(implicit hc: HeaderCarrier): Future[HttpResponse]

}

@Singleton
class SubscriptionConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)
                                       (implicit ec: ExecutionContext) extends SubscriptionConnector with DesConnector {

  val baseUrl: String = config.baseUrl("subscription")

  val url: String = s"$baseUrl/subscribe/individual"

  override def subscribe(subscriptionDetails: SubscriptionDetails)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.post(url, Json.toJson(subscriptionDetails), headers)(implicitly[Writes[JsValue]], hc.copy(authorization = None), ec)

}
