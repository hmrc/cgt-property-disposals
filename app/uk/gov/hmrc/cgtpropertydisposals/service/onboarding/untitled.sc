import play.api.libs.json.Json
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.SubscriptionService.DesSubscriptionDisplayDetails

val rawJson = """{
                |  "regime": "CGT",
                |  "subscriptionDetails": {
                |    "typeOfPersonDetails": {
                |      "typeOfPerson": ""
                |    },
                |    "isRegisteredWithId": false,
                |    "addressDetails": {
                |      "addressLine1": "Flat 9",
                |      "addressLine2": "121 Haverstock Hill",
                |      "addressLine3": "London",
                |      "countryCode": "GB",
                |      "postalCode": "NW34RS"
                |    },
                |    "contactDetails": {
                |      "contactName": "Estate Rivka Twersky",
                |      "emailAddress": "maya_twersky@yahoo.com"
                |    }
                |  }
                |}""".stripMargin

val res = Json.parse(rawJson).as[DesSubscriptionDisplayDetails]

System.out.println(res)

