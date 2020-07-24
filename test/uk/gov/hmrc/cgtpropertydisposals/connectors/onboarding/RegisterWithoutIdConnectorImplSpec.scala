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

package uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.name.IndividualName
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class RegisterWithoutIdConnectorImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      register-without-id {
         |      protocol = http
         |      host     = host
         |      port     = 123
         |    }
         |  }
         |}
         |
         |des {
         |  bearer-token = $desBearerToken
         |  environment  = $desEnvironment
         |}
         |""".stripMargin
    )
  )

  val connector =
    new RegisterWithoutIdConnectorImpl(mockHttp, new ServicesConfig(config))

  private val emptyJsonBody = "{}"

  "RegisterWithoutIdConnectorImpl" when {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    "handling request to subscribe for individuals" must {

      val expectedUrl         = "http://host:123/registration/02.00.00/individual"
      val registrationDetails = RegistrationDetails(
        IndividualName("name", "surname"),
        Email("email"),
        UkAddress(
          "addressLine1",
          Some("addressLine2"),
          Some("addressLine3"),
          Some("addressLine4"),
          Postcode("postcode")
        )
      )
      val referenceId: UUID   = UUID.randomUUID()

      val expectedRequest = Json.parse(
        s"""
          |{
          |  "regime": "CGT",
          |  "acknowledgementReference" : "${referenceId.toString.replaceAllLiterally("-", "")}",
          |  "isAnAgent": false,
          |  "isAGroup": false,
          |  "individual": {
          |    "firstName": "name",
          |    "lastName":  "surname"
          |  },
          |  "address" : {
          |    "addressLine1" : "addressLine1",
          |    "addressLine2" : "addressLine2",
          |    "addressLine3" : "addressLine3",
          |    "addressLine4" : "addressLine4",
          |    "postalCode" : "postcode",
          |    "countryCode" : "GB"
          |  },
          |  "contactDetails" : {
          |    "emailAddress" : "email"
          |  }
          |}
          |""".stripMargin
      )

      "do a post http call and return the result" in {
        List(
          HttpResponse(200, emptyJsonBody),
          HttpResponse(200, JsString("hi"), Map.empty[String, Seq[String]]),
          HttpResponse(500, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPost(expectedUrl, expectedHeaders, expectedRequest)(
              Some(httpResponse)
            )

            await(connector.registerWithoutId(registrationDetails, referenceId).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockPost(expectedUrl, expectedHeaders, expectedRequest)(None)

        await(connector.registerWithoutId(registrationDetails, referenceId).value).isLeft shouldBe true
      }

      "be able to convert non uk addresses" in {
        val httpResponse = HttpResponse(200, emptyJsonBody)

        val registrationDetails = RegistrationDetails(
          IndividualName("name", "surname"),
          Email("email"),
          NonUkAddress(
            "addressLine1",
            Some("addressLine2"),
            Some("addressLine3"),
            Some("addressLine4"),
            Some(Postcode("postcode")),
            Country("HK", Some("Hong Kong"))
          )
        )

        val expectedRequest = Json.parse(
          s"""
              |{
              |  "regime": "CGT",
              |  "acknowledgementReference" : "${referenceId.toString.replaceAllLiterally("-", "")}",
              |  "isAnAgent": false,
              |  "isAGroup": false,
              |  "individual": {
              |    "firstName": "name",
              |    "lastName":  "surname"
              |  },
              |  "address" : {
              |    "addressLine1" : "addressLine1",
              |    "addressLine2" : "addressLine2",
              |    "addressLine3" : "addressLine3",
              |    "addressLine4" : "addressLine4",
              |    "postalCode" : "postcode",
              |    "countryCode" : "HK"
              |  },
              |  "contactDetails" : {
              |    "emailAddress" : "email"
              |  }
              |}
              |""".stripMargin
        )

        mockPost(expectedUrl, expectedHeaders, expectedRequest)(Some(httpResponse))

        await(connector.registerWithoutId(registrationDetails, referenceId).value) shouldBe Right(httpResponse)
      }
    }

  }

}
