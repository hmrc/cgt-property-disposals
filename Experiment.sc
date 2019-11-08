import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.{KeyValuePair, Legacy, UpdateVerifiersRequest}
import play.api.libs.json.{Json, OFormat}

val s = UpdateVerifiersRequest(
    List(
      KeyValuePair("Postcode", "TF2 6NU"),
      KeyValuePair("Nino", "AB123456X")
    ),
    Legacy(
      List(
        KeyValuePair("Postcode", "TF2 6NU"),
        KeyValuePair("Nino", "AB123456X")
      )
    )
  )


println(Json.toJson(s))