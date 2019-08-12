package uk.gov.hmrc.cgtpropertydisposals.models

import play.api.libs.json.{Format, Json}

final case class SubscriptionDetails(
                                      forename: String,
                                      surname: String,
                                      emailAddress: String,
                                      address: Address,
                                      sapNumber: String
                                    )

object SubscriptionDetails {

  implicit val format: Format[SubscriptionDetails] = Json.format

}
