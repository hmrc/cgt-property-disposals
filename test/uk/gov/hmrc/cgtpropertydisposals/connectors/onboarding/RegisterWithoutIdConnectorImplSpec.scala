/*
 * Copyright 2023 HM Revenue & Customs
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

import com.typesafe.config.ConfigFactory
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.name.IndividualName
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.util.UUID

class RegisterWithoutIdConnectorImplSpec
    extends AnyWordSpec
    with Matchers
    with WireMockSupport
    with WireMockMethods
    with GuiceOneAppPerSuite
    with EitherValues {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      register-without-id {
         |        port     = $wireMockPort
         |    }
         |  }
         |}
         |
         |des {
         |  bearer-token = $desBearerToken
         |  environment  = $desEnvironment
         |}
         |create-internal-auth-token-on-start = false
         |""".stripMargin
    )
  )

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  val connector: RegisterWithoutIdConnector = app.injector.instanceOf[RegisterWithoutIdConnector]

  private val emptyJsonBody = "{}"

  "RegisterWithoutIdConnectorImpl" when {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    "handling request to subscribe for individuals" must {
      val expectedUrl         = "/registration/02.00.00/individual"
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
           |  "acknowledgementReference" : "${referenceId.toString.replaceAll("-", "")}",
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
            when(
              POST,
              expectedUrl,
              headers = expectedHeaders.toMap,
              body = Some(expectedRequest.toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.registerWithoutId(registrationDetails, referenceId).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error when the future fails" in {
        wireMockServer.stop()
        when(
          POST,
          expectedUrl,
          headers = expectedHeaders.toMap,
          body = Some(expectedRequest.toString())
        )

        await(connector.registerWithoutId(registrationDetails, referenceId).value).isLeft shouldBe true
        wireMockServer.start()
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
            Country("HK")
          )
        )

        val expectedRequest = Json.parse(
          s"""
             |{
             |  "regime": "CGT",
             |  "acknowledgementReference" : "${referenceId.toString.replaceAll("-", "")}",
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

        when(
          POST,
          expectedUrl,
          headers = expectedHeaders.toMap,
          body = Some(expectedRequest.toString())
        ).thenReturn(httpResponse.status, httpResponse.body)

        val response = await(connector.registerWithoutId(registrationDetails, referenceId).value).value
        response.status shouldBe httpResponse.status
        response.body   shouldBe httpResponse.body
      }
    }
  }
}
