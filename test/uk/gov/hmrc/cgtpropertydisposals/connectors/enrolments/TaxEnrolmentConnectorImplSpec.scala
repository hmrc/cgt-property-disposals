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

package uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments

import com.typesafe.config.ConfigFactory
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.{await, _}
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class TaxEnrolmentConnectorImplSpec
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
        |    tax-enrolments {
        |      port = $wireMockPort
        |    }
        |  }
        |}
        |create-internal-auth-token-on-start = false
        |""".stripMargin
    )
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  val connector: TaxEnrolmentConnector = app.injector.instanceOf[TaxEnrolmentConnector]

  private val emptyJsonBody = "{}"

  private val cgtReference       = CgtReference("XACGTP123456789")
  private val ukTaxEnrolment     =
    TaxEnrolmentRequest(
      "user-id",
      cgtReference.value,
      Address.UkAddress("line1", None, None, None, Postcode("OK113KO"))
    )
  private val nonUKTaxEnrolment  = TaxEnrolmentRequest(
    "user-id",
    cgtReference.value,
    Address.NonUkAddress("line1", None, None, None, None, Country("NZ"))
  )
  private val ukEnrolmentRequest =
    Enrolments(List(KeyValuePair("Postcode", "OK113KO")), List(KeyValuePair("CGTPDRef", cgtReference.value)))

  private val nonUkEnrolmentRequest =
    Enrolments(List(KeyValuePair("CountryCode", "NZ")), List(KeyValuePair("CGTPDRef", cgtReference.value)))

  private val updateVerifiersRequest = UpdateVerifiersRequest(
    "ggCredId",
    SubscribedUpdateDetails(
      SubscribedDetails(
        Left(TrustName("ABC Corp")),
        Email("ab@gmail.com"),
        Address.UkAddress("line1", None, None, None, Postcode("OK113KO")),
        ContactName("Stephen Wood"),
        cgtReference,
        None,
        registeredWithId = true
      ),
      SubscribedDetails(
        Left(TrustName("ABC Corp")),
        Email("ab@gmail.com"),
        Address.UkAddress("line1", None, None, None, Postcode("TF2 6NU")),
        ContactName("Stephen Wood"),
        cgtReference,
        None,
        registeredWithId = true
      )
    )
  )

  private val taxEnrolmentUpdateRequest = TaxEnrolmentUpdateRequest(
    List(
      KeyValuePair("Postcode", "OK113KO")
    ),
    Legacy(
      List(
        KeyValuePair("Postcode", "TF2 6NU")
      )
    )
  )

  "Tax Enrolment Connector" when {
    "it receives a request to update the verifiers" must {
      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              PUT,
              s"/tax-enrolments/enrolments/HMRC-CGT-PD~CGTPDRef~${cgtReference.value}",
              body = Some(Json.toJson(taxEnrolmentUpdateRequest).toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.updateVerifiers(updateVerifiersRequest).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error" when {
        "the future fails" in {
          wireMockServer.stop()
          when(
            PUT,
            s"/tax-enrolments/enrolments/HMRC-CGT-PD~CGTPDRef~${cgtReference.value}",
            body = Some(Json.toJson(taxEnrolmentUpdateRequest).toString())
          )

          await(connector.updateVerifiers(updateVerifiersRequest).value).isLeft shouldBe true
          wireMockServer.start()
        }
      }
    }

    "it receives a request to enrol a UK user it" must {
      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              PUT,
              "/tax-enrolments/service/HMRC-CGT-PD/enrolment",
              body = Some(Json.toJson(ukEnrolmentRequest).toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.allocateEnrolmentToGroup(ukTaxEnrolment).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }
    }

    "it receives a request to enrol a non UK user it" must {
      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              PUT,
              "/tax-enrolments/service/HMRC-CGT-PD/enrolment",
              body = Some(Json.toJson(nonUkEnrolmentRequest).toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.allocateEnrolmentToGroup(nonUKTaxEnrolment).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }
    }

    "return an error" when {
      "the future fails" in {
        wireMockServer.stop()
        when(
          PUT,
          "/tax-enrolments/service/HMRC-CGT-PD/enrolment",
          body = Some(Json.toJson(ukEnrolmentRequest).toString())
        )

        await(connector.allocateEnrolmentToGroup(ukTaxEnrolment).value).isLeft shouldBe true
        wireMockServer.start()
      }
    }
  }
}
