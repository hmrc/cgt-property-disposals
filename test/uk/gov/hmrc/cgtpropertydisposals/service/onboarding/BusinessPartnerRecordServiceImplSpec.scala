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

package uk.gov.hmrc.cgtpropertydisposals.service.onboarding

import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.{BusinessPartnerRecordConnector, SubscriptionConnector}
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessPartnerRecordServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockBprConnector: BusinessPartnerRecordConnector = mock[BusinessPartnerRecordConnector]

  val mockSubscriptionConnector: SubscriptionConnector = mock[SubscriptionConnector]

  val nonIsoCountryCode = "XZ"

  val config = Configuration(
    ConfigFactory.parseString(s"""des.non-iso-country-codes = ["$nonIsoCountryCode"]""".stripMargin)
  )

  val service =
    new BusinessPartnerRecordServiceImpl(
      mockBprConnector,
      mockSubscriptionConnector,
      config,
      MockMetrics.metrics
    )

  def mockGetBPR(bprRequest: BusinessPartnerRecordRequest)(response: Either[Error, HttpResponse]) =
    (mockBprConnector
      .getBusinessPartnerRecord(_: BusinessPartnerRecordRequest)(_: HeaderCarrier))
      .expects(bprRequest, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscriptionStatus(sapNumber: SapNumber)(response: Either[Error, HttpResponse]) =
    (mockSubscriptionConnector
      .getSubscriptionStatus(_: SapNumber)(_: HeaderCarrier))
      .expects(sapNumber, *)
      .returning(EitherT(Future.successful(response)))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val bprRequest = sample[BusinessPartnerRecordRequest]

  val (name, trustName) = sample[IndividualName] -> sample[TrustName]

  val sapNumber = sample[SapNumber]

  "The BusinessPartnerRecordServiceImpl" when {

    "getting a business partner record" must {

      def expectedBpr(address: Address, name: Either[TrustName, IndividualName]) =
        BusinessPartnerRecord(Some("email"), address, sapNumber, name)

      def bprResponseJson(
        addressBody: String,
        organisationName: Option[TrustName],
        individualName: Option[IndividualName]
      ) =
        Json.parse(s"""
           |{
           |  ${individualName
                        .map(n => s""""individual":{"firstName":"${n.firstName}","lastName":"${n.lastName}"},""")
                        .getOrElse("")}
           |  "contactDetails" : {
           |    "emailAddress" : "email"
           |  },
           |  "sapNumber" : "${sapNumber.value}",
           |  ${organisationName
                        .map(trustName => s""""organisation":{"organisationName":"${trustName.value}"},""")
                        .getOrElse("")}
           |  $addressBody
           |}
           |""".stripMargin)

      "return an error" when {

        def testGetBprError(response: => Either[Error, HttpResponse]) = {
          mockGetBPR(bprRequest)(response)
          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        def testGetSubscriptionStatusError(response: => Either[Error, HttpResponse]) = {
          val bprResponseBody = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "postalCode"   : "postcode",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            None,
            Some(name)
          )

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(bprResponseBody))))
            mockGetSubscriptionStatus(sapNumber)(response)
          }

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the response to get BPR comes back with a status other than 200" in {
          List(400, 401, 403, 404, 500, 501, 502).foreach { status =>
            testGetBprError(Right(HttpResponse(status)))
          }
        }

        "the json in the response to get BPR cannot be parsed" in {
          testGetBprError(Right(HttpResponse(200, Some(JsNumber(0)))))
          testGetBprError(Right(HttpResponse(200, responseString = Some("hello"))))

        }

        "there is no json in the http response body when getting a BPR" in {
          testGetBprError(Right(HttpResponse(200)))
        }

        "the call to get a BPR fails" in {
          mockGetBPR(bprRequest)(Left(Error(new Exception("Oh no!"))))
          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the call to get a BPR comes back with status 200 and valid JSON but a postcode cannot " +
          "be found for a uk address" in {
          val body = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            Some(trustName),
            None
          )

          testGetBprError(Right(HttpResponse(200, Some(body))))
        }

        "there is no organisation name or individual name in the response body when getting a BPR" in {
          val body = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "postalCode"   : "postcode",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            None,
            None
          )

          testGetBprError(Right(HttpResponse(200, Some(body))))
        }

        "there is both an organisation name and an individual name in the response body when getting a BPR" in {
          val body = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "postalCode"   : "postcode",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            Some(trustName),
            Some(name)
          )

          testGetBprError(Right(HttpResponse(200, Some(body))))
        }

        "a country code is returned for which a name cannot be found and which hasn't been configured " +
          "as a non-ISO country code when getting a BPR" in {
          val body = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "postalCode"   : "postcode",
              |    "countryCode"  : "XX"
              |  }
              |""".stripMargin,
            Some(trustName),
            Some(name)
          )

          testGetBprError(Right(HttpResponse(200, Some(body))))
        }

        "the call to get subscription status fails" in {
          testGetSubscriptionStatusError(Left(Error("")))
        }

        "the call to get subscription status returns with an error status code" in {
          testGetSubscriptionStatusError(Right(HttpResponse(500)))
        }

        "the response body to get subscription status contains no JSON" in {
          testGetSubscriptionStatusError(Right(HttpResponse(200)))
        }

        "the response body to get subscription status contains JSON which cannot be parsed" in {
          testGetSubscriptionStatusError(Right(HttpResponse(200, Some(JsNumber(1)))))

        }

        "the response body to get subscription status does not contain a CGT reference id when " +
          "the status is subscribed" in {
          List(
            Json.parse("""{ "subscriptionStatus": "SUCCESSFUL" }"""),
            Json.parse("""{ "subscriptionStatus": "SUCCESSFUL", "idType": "TYPE", "idValue": "value"}"""),
            Json.parse("""{ "subscriptionStatus": "SUCCESSFUL", "idType": "ZCGT"}""")
          ).foreach { json =>
            withClue(s"For JSON $json ") {
              testGetSubscriptionStatusError(Right(HttpResponse(200, Some(json))))
            }
          }
        }

        "the response body to get subscription status contains an unknown status" in {
          testGetSubscriptionStatusError(
            Right(
              HttpResponse(
                200,
                Some(
                  Json.parse("""{ "subscriptionStatus" : "HELLO" }""")
                )
              )
            )
          )

        }
      }

      "return a BPR response" when {

        val notSubscribedJsonBody = Json.parse("""{ "subscriptionStatus" : "NO_FORM_BUNDLE_FOUND" }""")

        "the call comes back with status 200 with valid JSON and a valid UK address" in {
          val body = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "postalCode"   : "postcode",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            None,
            Some(name)
          )

          val expectedAddress = UkAddress("line1", Some("line2"), Some("line3"), Some("line4"), Postcode("postcode"))

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))
            mockGetSubscriptionStatus(sapNumber)(Right(HttpResponse(200, Some(notSubscribedJsonBody))))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Right(name))), None)
          )
        }

        "the call comes back with status 200 with valid JSON and a valid non-UK address where a country name exists" in {
          val body = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "countryCode"  : "HK"
              |  }
              |""".stripMargin,
            Some(trustName),
            None
          )

          val expectedAddress =
            NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country("HK", Some("Hong Kong")))

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))
            mockGetSubscriptionStatus(sapNumber)(Right(HttpResponse(200, Some(notSubscribedJsonBody))))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Left(trustName))), None)
          )
        }

        "the call comes back with status 200 with valid JSON and a valid non-UK address where a country name does not exist " +
          "but the country code has been configured as a non-ISO country code" in {
          val body = bprResponseJson(
            s"""
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "countryCode"  : "$nonIsoCountryCode"
              |  }
              |""".stripMargin,
            Some(trustName),
            None
          )

          val expectedAddress =
            NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country(nonIsoCountryCode, None))

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))
            mockGetSubscriptionStatus(sapNumber)(Right(HttpResponse(200, Some(notSubscribedJsonBody))))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Left(trustName))), None)
          )
        }

        "the call comes back with status 404 and there is a valid JSON body" in {
          val body = Json.parse(
            """
              |{
              |  "code" : "NOT_FOUND",
              |  "reason" : "The remote endpoint has indicated that no data can be found"
              |}
              |""".stripMargin
          )

          mockGetBPR(bprRequest)(Right(HttpResponse(404, Some(body))))

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(None, None)
          )
        }

        "the call to get a BPR is successful and the user is already subscribed" in {
          val cgtReference = CgtReference("cgt")

          val bprBody = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "postalCode"   : "postcode",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            None,
            Some(name)
          )

          val statusBody = Json.parse(s"""
                                         |{
                                         |  "subscriptionStatus" : "SUCCESSFUL",
                                         |  "idType" : "ZCGT",
                                         |  "idValue" : "${cgtReference.value}"
                                         |}""".stripMargin)

          val expectedAddress = UkAddress("line1", None, None, None, Postcode("postcode"))

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(bprBody))))
            mockGetSubscriptionStatus(sapNumber)(Right(HttpResponse(200, Some(statusBody))))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Right(name))), Some(cgtReference))
          )
        }

        "the call to get a BPR is successful and the subscription status is not 'SUCCESSFUL'" in {
          List(
            "NO_FORM_BUNDLE_FOUND",
            "REG_FORM_RECEIVED",
            "SENT_TO_DS",
            "DS_OUTCOME_IN_PROGRESS",
            "REJECTED",
            "IN_PROCESSING",
            "CREATE_FAILED",
            "WITHDRAWAL",
            "SENT_TO_RCM",
            "APPROVED_WITH_CONDITIONS",
            "REVOKED",
            "DE-REGISTERED",
            "CONTRACT_OBJECT_INACTIVE"
          ).foreach { subscriptionStatus =>
            withClue(s"For subscription status '$subscriptionStatus' ") {
              val bprBody = bprResponseJson(
                """
                  |"address" : {
                  |    "addressLine1" : "line1",
                  |    "postalCode"   : "postcode",
                  |    "countryCode"  : "GB"
                  |  }
                  |""".stripMargin,
                None,
                Some(name)
              )

              val statusBody      = Json.parse(s"""{ "subscriptionStatus" : "$subscriptionStatus"}""".stripMargin)
              val expectedAddress = UkAddress("line1", None, None, None, Postcode("postcode"))

              inSequence {
                mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(bprBody))))
                mockGetSubscriptionStatus(sapNumber)(Right(HttpResponse(200, Some(statusBody))))
              }

              await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
                BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Right(name))), None)
              )
            }
          }
        }
      }
    }
  }

}
