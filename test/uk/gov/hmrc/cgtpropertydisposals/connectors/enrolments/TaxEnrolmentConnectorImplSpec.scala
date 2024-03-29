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
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers.{await, _}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentConnectorImplSpec extends AnyWordSpec with Matchers with IdiomaticMockito with HttpSupport {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  private val config = Configuration(
    ConfigFactory.parseString(
      """
        |microservice {
        |  services {
        |    tax-enrolments {
        |      protocol = http
        |      host     = host
        |      port     = 123
        |    }
        |  }
        |}
        |""".stripMargin
    )
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val connector = new TaxEnrolmentConnectorImpl(mockHttp, new ServicesConfig(config))

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
          HttpResponse(204, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPut[TaxEnrolmentUpdateRequest](
              s"http://host:123/tax-enrolments/enrolments/HMRC-CGT-PD~CGTPDRef~${cgtReference.value}",
              taxEnrolmentUpdateRequest
            )(Some(httpResponse))
            await(
              connector
                .updateVerifiers(updateVerifiersRequest)
                .value
            ) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error" when {
        "the future fails" in {
          mockPut[TaxEnrolmentUpdateRequest](
            s"http://host:123/tax-enrolments/enrolments/HMRC-CGT-PD~CGTPDRef~${cgtReference.value}",
            taxEnrolmentUpdateRequest
          )(None)
          await(
            connector
              .updateVerifiers(updateVerifiersRequest)
              .value
          ).isLeft shouldBe true
        }
      }
    }

    "it receives a request to enrol a UK user it" must {
      "make a http put call and return a result" in {
        List(
          HttpResponse(204, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPut[Enrolments](
              s"http://host:123/tax-enrolments/service/HMRC-CGT-PD/enrolment",
              ukEnrolmentRequest
            )(Some(httpResponse))

            await(connector.allocateEnrolmentToGroup(ukTaxEnrolment).value) shouldBe Right(httpResponse)
          }
        }
      }
    }

    "it receives a request to enrol a non UK user it" must {
      "make a http put call and return a result" in {
        List(
          HttpResponse(204, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPut[Enrolments](
              s"http://host:123/tax-enrolments/service/HMRC-CGT-PD/enrolment",
              nonUkEnrolmentRequest
            )(Some(httpResponse))

            await(connector.allocateEnrolmentToGroup(nonUKTaxEnrolment).value) shouldBe Right(httpResponse)
          }
        }
      }
    }

    "return an error" when {
      "the future fails" in {
        mockPut[Enrolments](
          s"http://host:123/tax-enrolments/service/HMRC-CGT-PD/enrolment",
          ukEnrolmentRequest
        )(None)
        await(connector.allocateEnrolmentToGroup(ukTaxEnrolment).value).isLeft shouldBe true
      }
    }
  }
}
