package uk.gov.hmrc.cgtpropertydisposals.models.des

import play.api.libs.json.Json
import uk.gov.hmrc.cgtpropertydisposals.models.SubscriptionDetails

case class DesSubscribedDetailsResponse(
  regime: String,
  subscriptionDetails: SubscriptionDetails,
  isRegisteredWithId: Boolean,
  addressDetails: AddressDetails,
  contactDetails: ContactDetails
)

object DesSubscribedDetailsResponse {
  implicit val format = Json.format[DesSubscribedDetailsResponse]
}
