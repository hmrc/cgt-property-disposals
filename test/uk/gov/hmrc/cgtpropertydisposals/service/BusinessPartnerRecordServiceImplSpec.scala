/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.service

import cats.data.EitherT
import com.typesafe.config.{Config, ConfigFactory}
import org.scalamock.scalatest.MockFactory
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.BusinessPartnerRecordConnector
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.models.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, address, sample}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class BusinessPartnerRecordServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector: BusinessPartnerRecordConnector = mock[BusinessPartnerRecordConnector]

  val mockAuditService: AuditService = mock[AuditService]

  val nonIsoCountryCode = "XZ"

  val config = Configuration(ConfigFactory.parseString(s"""
      |des.non-iso-country-codes = ["$nonIsoCountryCode"]
      |""".stripMargin))

  val service = new BusinessPartnerRecordServiceImpl(mockConnector, config, mockAuditService)

  def mockRegistrationResponse(httpStatus: Int, httpBody: String, path: String)(response: Unit) =
    (mockAuditService
      .sendRegistrationResponse(_: Int, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(httpStatus, *, path, *, *)
      .returning(response)

  def mockGetBPR(bprRequest: BusinessPartnerRecordRequest)(response: Either[Error, HttpResponse]) =
    (mockConnector
      .getBusinessPartnerRecord(_: BusinessPartnerRecordRequest)(_: HeaderCarrier))
      .expects(bprRequest, *)
      .returning(EitherT(Future.successful(response)))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val bprRequest = sample[BusinessPartnerRecordRequest]

  val (name, trustName) = sample[IndividualName] -> sample[TrustName]

  "The BusinessPartnerRecordServiceImpl" when {

    "getting a business partner record" must {

      def expectedBpr(address: Address, name: Either[TrustName, IndividualName]) =
        BusinessPartnerRecord(Some("email"), address, "1234567890", name)

      def responseJson(
        addressBody: String,
        organisationName: Option[TrustName],
        individualName: Option[IndividualName]) =
        Json.parse(s"""
           |{
           |  ${individualName
                        .map(n => s""""individual":{"firstName":"${n.firstName}","lastName":"${n.lastName}"},""")
                        .getOrElse("")}
           |  "contactDetails" : {
           |    "emailAddress" : "email"
           |  },
           |  "sapNumber" : "1234567890",
           |  ${organisationName
                        .map(trustName => s""""organisation":{"organisationName":"${trustName.value}"},""")
                        .getOrElse("")}
           |  $addressBody
           |}
           |""".stripMargin)

      "return an error" when {

        def testError(response: => Either[Error, HttpResponse]) = {
          mockGetBPR(bprRequest)(response)
          response match {
            case Left(a) => mockRegistrationResponse(500, "", "/business-partner-record")(())
            case Right(b) =>
              mockRegistrationResponse(b.status, b.body, "/business-partner-record")(())
          }

          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the response comes back with a status other than 200" in {
          List(400, 401, 403, 404, 500, 501, 502).foreach { status =>
            testError(Right(HttpResponse(status)))
          }
        }

        "the json cannot be parsed" in {
          testError(Right(HttpResponse(200, Some(JsNumber(0)))))
          testError(Right(HttpResponse(200, responseString = Some("hello"))))

        }

        "there is no json in the http response body" in {
          testError(Right(HttpResponse(200)))
        }

        "the call to get a BPR fails" in {
          mockGetBPR(bprRequest)(Left(Error(new Exception("Oh no!"))))
          await(service.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }

        "the call comes back with status 200 and valid JSON but a postcode cannot " +
          "be found for a uk address" in {
          val body = responseJson(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            Some(trustName),
            None
          )

          testError(Right(HttpResponse(200, Some(body))))
        }

        "there is no organisation name or individual name in the response body" in {
          val body = responseJson(
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

          testError(Right(HttpResponse(200, Some(body))))
        }

        "there is both an organisation name and an individual name in the response body" in {
          val body = responseJson(
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

          testError(Right(HttpResponse(200, Some(body))))
        }

        "a country code is returned for which a name cannot be found and which hasn't been configured " +
          "as a non-ISO country code" in {
          val body = responseJson(
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

          testError(Right(HttpResponse(200, Some(body))))
        }

      }

      "return a BPR response" when {

        "the call comes back with status 200 with valid JSON and a valid UK address" in {
          val body = responseJson(
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

          mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))
          mockRegistrationResponse(200, body.toString(), "/business-partner-record")(())

          val expectedAddress = UkAddress("line1", Some("line2"), Some("line3"), Some("line4"), "postcode")
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Right(name))))
          )
        }

        "the call comes back with status 200 with valid JSON and a valid non-UK address where a country name exists" in {
          val body = responseJson(
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

          mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))
          mockRegistrationResponse(200, body.toString(), "/business-partner-record")(())

          val expectedAddress =
            NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country("HK", Some("Hong Kong")))
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Left(trustName))))
          )
        }

        "the call comes back with status 200 with valid JSON and a valid non-UK address where a country name does not exist " +
          "but the country code has been configured as a non-ISO country code" in {
          val body = responseJson(
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

          mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))
          mockRegistrationResponse(200, "", "/business-partner-record")(())

          val expectedAddress =
            NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country(nonIsoCountryCode, None))
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(Some(expectedBpr(expectedAddress, Left(trustName))))
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
          mockRegistrationResponse(404, body.toString(), "/business-partner-record")(())

          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            BusinessPartnerRecordResponse(None)
          )
        }

      }

    }

  }

}
