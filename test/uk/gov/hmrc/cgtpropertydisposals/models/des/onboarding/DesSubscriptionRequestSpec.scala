/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.SapNumber
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionDetails

class DesSubscriptionRequestSpec extends WordSpec with Matchers {

  "DesSubscriptionRequest" must {

    "have a method which creates a DesSubscriptionRequest" when {

      "given details for a trust" in {
        val subscriptionDetails = SubscriptionDetails(
          Left(TrustName("name")),
          ContactName("contact"),
          Email("email"),
          NonUkAddress(
            "line1",
            Some("line2"),
            Some("line3"),
            Some("line4"),
            Some(Postcode("postcode")),
            Country("HK", Some("Hong Kong"))
          ),
          SapNumber("sap")
        )

        Json.toJson(DesSubscriptionRequest(subscriptionDetails)) shouldBe Json.parse(
          """
            |{
            |  "regime": "CGT",
            |  "identity": {
            |    "idType": "sapNumber",
            |    "idValue": "sap"
            |  },
            |  "subscriptionDetails": {
            |    "typeOfPersonDetails": {
            |      "typeOfPerson": "Trustee",
            |      "organisationName": "name"
            |    },
            |    "addressDetails": {
            |      "addressLine1": "line1",
            |      "addressLine2": "line2",
            |      "addressLine3": "line3",
            |      "addressLine4": "line4",
            |      "postalCode": "postcode",
            |      "countryCode": "HK"
            |    },
            |    "contactDetails": {
            |      "contactName": "contact",
            |      "emailAddress": "email"
            |    }
            |  }
            |}
            |""".stripMargin
        )
      }

      "given details for an individual" in {
        val subscriptionDetails = SubscriptionDetails(
          Right(IndividualName("name", "surname")),
          ContactName("contact"),
          Email("email"),
          UkAddress("line1", Some("line2"), Some("town"), Some("county"), Postcode("postcode")),
          SapNumber("sap")
        )

        Json.toJson(DesSubscriptionRequest(subscriptionDetails)) shouldBe Json.parse(
          """
            |{
            |  "regime": "CGT",
            |  "identity": {
            |    "idType": "sapNumber",
            |    "idValue": "sap"
            |  },
            |  "subscriptionDetails": {
            |    "typeOfPersonDetails": {
            |      "typeOfPerson": "Individual",
            |      "firstName": "name",
            |      "lastName": "surname"
            |    },
            |    "addressDetails": {
            |      "addressLine1": "line1",
            |      "addressLine2": "line2",
            |      "addressLine3": "town",
            |      "addressLine4": "county",
            |      "postalCode": "postcode",
            |      "countryCode": "GB"
            |    },
            |    "contactDetails": {
            |      "contactName": "contact",
            |      "emailAddress": "email"
            |    }
            |  }
            |}
            |""".stripMargin
        )

      }

    }

  }

}
