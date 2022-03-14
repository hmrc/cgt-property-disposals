/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.{Clock, LocalDateTime, ZoneId, ZoneOffset}

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.BusinessPartnerRecordConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.BusinessPartnerRecordRequest.IndividualBusinessPartnerRecordRequest
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.service.email.EmailService
import uk.gov.hmrc.cgtpropertydisposals.service.enrolments.{EnrolmentStoreProxyService, TaxEnrolmentService}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessPartnerRecordServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockBprConnector: BusinessPartnerRecordConnector = mock[BusinessPartnerRecordConnector]

  val mockEnrolmentStoreProxyService: EnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  val mockEmailService: EmailService = mock[EmailService]

  val mockSubscriptionService: SubscriptionService = mock[SubscriptionService]

  val mockTaxEnrolmentService: TaxEnrolmentService = mock[TaxEnrolmentService]

  val dummyTimestamp = LocalDateTime.now()

  val service =
    new BusinessPartnerRecordServiceImpl(
      mockBprConnector,
      mockEnrolmentStoreProxyService,
      mockEmailService,
      mockSubscriptionService,
      mockTaxEnrolmentService,
      MockMetrics.metrics
    ) {
      override val clock: Clock = Clock.fixed(dummyTimestamp.toInstant(ZoneOffset.UTC), ZoneId.of("Z"))
    }

  def mockGetBPR(bprRequest: BusinessPartnerRecordRequest)(response: Either[Error, HttpResponse]) =
    (mockBprConnector
      .getBusinessPartnerRecord(_: BusinessPartnerRecordRequest)(_: HeaderCarrier))
      .expects(bprRequest, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscriptionStatus(sapNumber: SapNumber)(response: Either[Error, Option[CgtReference]]) =
    (mockSubscriptionService
      .getSubscriptionStatus(_: SapNumber)(_: HeaderCarrier))
      .expects(sapNumber, *)
      .returning(EitherT(Future.successful(response)))

  def mockEnrolmentExists(cgtReference: CgtReference)(result: Either[Error, Boolean]) =
    (mockEnrolmentStoreProxyService
      .cgtEnrolmentExists(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGetSubscription(cgtReference: CgtReference)(response: Either[Error, Option[SubscribedDetails]]): Unit =
    (mockSubscriptionService
      .getSubscription(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
      .returning(EitherT.fromEither(response))

  def mockAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Unit]
  ) =
    (mockTaxEnrolmentService
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT(Future.successful(response)))

  def mockSendSubscriptionConfirmationEmail(cgtReference: CgtReference, email: Email, contactName: ContactName)(
    result: Either[Error, Unit]
  ) =
    (mockEmailService
      .sendSubscriptionConfirmationEmail(_: CgtReference, _: Email, _: ContactName)(_: HeaderCarrier, _: Request[_]))
      .expects(cgtReference, email, contactName, *, *)
      .returning(EitherT.fromEither[Future](result))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val request: Request[_] = FakeRequest()

  val (name, trustName) = sample[IndividualName] -> sample[TrustName]

  val sapNumber = sample[SapNumber]

  private val emptyJsonBody = "{}"

  "The BusinessPartnerRecordServiceImpl" when {

    "getting a business partner record" must {

      def expectedBpr(address: Option[Address], name: Either[TrustName, IndividualName]) =
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

        def testGetBprError(bprRequest: BusinessPartnerRecordRequest, response: => Either[Error, HttpResponse]) = {
          mockGetBPR(bprRequest)(response)

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the response to get BPR comes back with a status other than 200" in {
          List(400, 401, 403, 404, 500, 501, 502).foreach(status =>
            testGetBprError(sample[BusinessPartnerRecordRequest], Right(HttpResponse(status, "")))
          )
        }

        "the json in the response to get BPR cannot be parsed" in {
          testGetBprError(sample[BusinessPartnerRecordRequest], Right(HttpResponse(200, "hello")))
        }

        "there is no json in the http response body when getting a BPR" in {
          testGetBprError(sample[BusinessPartnerRecordRequest], Right(HttpResponse(200, emptyJsonBody)))
        }

        "the call to get a BPR fails" in {
          val bprRequest = sample[BusinessPartnerRecordRequest]

          mockGetBPR(bprRequest)(Left(Error(new Exception("Oh no!"))))

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
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

          testGetBprError(
            sample[BusinessPartnerRecordRequest],
            Right(HttpResponse(200, body, Map.empty[String, Seq[String]]))
          )
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

          testGetBprError(
            sample[BusinessPartnerRecordRequest],
            Right(HttpResponse(200, body, Map.empty[String, Seq[String]]))
          )
        }

        "the call to get subscription status fails" in {
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

          val bprRequest = sample[BusinessPartnerRecordRequest]

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, bprResponseBody, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Left(Error("")))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the call to see if an enrolment already exists fails" in {
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

          val bprRequest = sample[IndividualBusinessPartnerRecordRequest]
            .copy(createNewEnrolmentIfMissing = true)

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, bprBody, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(Some(cgtReference)))
            mockEnrolmentExists(cgtReference)(Left(Error("")))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the call to get subscription details fails" in {
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

          val bprRequest = sample[IndividualBusinessPartnerRecordRequest]
            .copy(createNewEnrolmentIfMissing = true)
          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, bprBody, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(Some(cgtReference)))
            mockEnrolmentExists(cgtReference)(Right(false))
            mockGetSubscription(cgtReference)(Left(Error("")))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the call to get subsription details does not return any details" in {
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

          val bprRequest = sample[IndividualBusinessPartnerRecordRequest]
            .copy(createNewEnrolmentIfMissing = true)

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, bprBody, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(Some(cgtReference)))
            mockEnrolmentExists(cgtReference)(Right(false))
            mockGetSubscription(cgtReference)(Right(None))

          }

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true

        }

        "the call to enrol the user fails" in {
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

          val ggCredId            = "ggCredId"
          val bprRequest          = sample[IndividualBusinessPartnerRecordRequest]
            .copy(createNewEnrolmentIfMissing = true, ggCredId = ggCredId)
          val subscribedDetails   = sample[SubscribedDetails]
          val taxEnrolmentRequest = TaxEnrolmentRequest(
            ggCredId,
            cgtReference.value,
            subscribedDetails.address,
            dummyTimestamp
          )

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, bprBody, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(Some(cgtReference)))
            mockEnrolmentExists(cgtReference)(Right(false))
            mockGetSubscription(cgtReference)(Right(Some(subscribedDetails)))
            mockAllocateEnrolment(taxEnrolmentRequest)(Left(Error("")))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

      }

      "return a BPR response" when {

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
          val bprRequest      = sample[BusinessPartnerRecordRequest]

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, body, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(None))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(Some(expectedAddress), Right(name))), None, None)
          )
        }

        "the call comes back with status 200 with valid JSON and a uk address that does not have a postcode" in {
          val body = bprResponseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            None,
            Some(name)
          )

          val bprRequest = sample[BusinessPartnerRecordRequest]

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, body, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(None))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(None, Right(name))), None, None)
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
            NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country("HK"))

          val bprRequest = sample[BusinessPartnerRecordRequest]

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, body, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(None))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(Some(expectedAddress), Left(trustName))), None, None)
          )
        }

        "the call comes back with status 200 with valid JSON and a country code where a country name does not exist" in {
          val body = bprResponseJson(
            s"""
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "countryCode"  : "ZZ"
              |  }
              |""".stripMargin,
            Some(trustName),
            None
          )

          val bprRequest = sample[BusinessPartnerRecordRequest]

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, body, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(None))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(None, Left(trustName))), None, None)
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

          val bprRequest = sample[BusinessPartnerRecordRequest]

          mockGetBPR(bprRequest)(Right(HttpResponse(404, body, Map.empty[String, Seq[String]])))

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(None, None, None)
          )
        }

        "the call to get a BPR is successful and the user is already subscribed and creating a new enrolment is disabled" in {
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

          val expectedAddress = UkAddress("line1", None, None, None, Postcode("postcode"))
          val bprRequest      = sample[IndividualBusinessPartnerRecordRequest].copy(createNewEnrolmentIfMissing = false)

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, bprBody, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(Some(cgtReference)))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(
              Some(expectedBpr(Some(expectedAddress), Right(name))),
              Some(cgtReference),
              None
            )
          )
        }

        "the call to get a BPR is successful and the user is already subscribed and creating a new enrolment is enabled and " +
          "an enrolment already exists" in {
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

            val expectedAddress = UkAddress("line1", None, None, None, Postcode("postcode"))
            val bprRequest      = sample[IndividualBusinessPartnerRecordRequest].copy(createNewEnrolmentIfMissing = true)

            inSequence {
              mockGetBPR(bprRequest)(Right(HttpResponse(200, bprBody, Map.empty[String, Seq[String]])))
              mockGetSubscriptionStatus(sapNumber)(Right(Some(cgtReference)))
              mockEnrolmentExists(cgtReference)(Right(true))
            }

            await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
              BusinessPartnerRecordResponse(
                Some(expectedBpr(Some(expectedAddress), Right(name))),
                Some(cgtReference),
                None
              )
            )
          }

        "the call to get a BPR is successful and the user is already subscribed and creating a new enrolment is enabled and " +
          "an enrolment does not already exists" in {
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

            val expectedAddress     = UkAddress("line1", None, None, None, Postcode("postcode"))
            val ggCredId            = "ggCredId"
            val bprRequest          = sample[IndividualBusinessPartnerRecordRequest]
              .copy(createNewEnrolmentIfMissing = true, ggCredId = ggCredId)
            val subscribedDetails   = sample[SubscribedDetails]
            val taxEnrolmentRequest = TaxEnrolmentRequest(
              ggCredId,
              cgtReference.value,
              subscribedDetails.address,
              dummyTimestamp
            )

            inSequence {
              mockGetBPR(bprRequest)(Right(HttpResponse(200, bprBody, Map.empty[String, Seq[String]])))
              mockGetSubscriptionStatus(sapNumber)(Right(Some(cgtReference)))
              mockEnrolmentExists(cgtReference)(Right(false))
              mockGetSubscription(cgtReference)(Right(Some(subscribedDetails)))
              mockAllocateEnrolment(taxEnrolmentRequest)(Right(()))
              mockSendSubscriptionConfirmationEmail(
                cgtReference,
                subscribedDetails.emailAddress,
                subscribedDetails.contactName
              )(Right(()))
            }

            await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
              BusinessPartnerRecordResponse(
                Some(expectedBpr(Some(expectedAddress), Right(name))),
                Some(cgtReference),
                Some(subscribedDetails)
              )
            )
          }

        "the call comes back with slashes in the organisation name" in {
          val body = bprResponseJson(
            s"""
               |"address" : {
               |    "addressLine1" : "line1",
               |    "addressLine2" : "line2",
               |    "addressLine3" : "line3",
               |    "addressLine4" : "line4",
               |    "countryCode"  : "HK"
               |  }
               |""".stripMargin,
            Some(TrustName("a/trust\\\\with/\\\\slashes")),
            None
          )

          val expectedAddress =
            NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country("HK"))

          val bprRequest = sample[BusinessPartnerRecordRequest]

          inSequence {
            mockGetBPR(bprRequest)(Right(HttpResponse(200, body, Map.empty[String, Seq[String]])))
            mockGetSubscriptionStatus(sapNumber)(Right(None))
          }

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(
              Some(expectedBpr(Some(expectedAddress), Left(TrustName("a-trust-with--slashes")))),
              None,
              None
            )
          )

        }

      }
    }
  }

}
