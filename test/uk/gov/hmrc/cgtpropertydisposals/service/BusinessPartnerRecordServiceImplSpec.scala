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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.BusinessPartnerRecordConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.BprRequest.Organisation
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, BprRequest, BusinessPartnerRecord, DateOfBirth, Error, NINO, Name, SAUTR, sample}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessPartnerRecordServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector: BusinessPartnerRecordConnector = mock[BusinessPartnerRecordConnector]

  val service = new BusinessPartnerRecordServiceImpl(mockConnector)

  def mockGetBPR(bprRequest: BprRequest)(response: Either[Error, HttpResponse]) =
    (mockConnector
      .getBusinessPartnerRecord(_: BprRequest)(_: HeaderCarrier))
      .expects(bprRequest, *)
      .returning(EitherT(Future.successful(response)))

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val nino                       = NINO("AB123456C")
  val name                       = Name("forename", "surname")
  val dateOfBirth                = DateOfBirth(LocalDate.parse("2000-11-12", DateTimeFormatter.ISO_LOCAL_DATE))

  "The BusinessPartnerRecordServiceImpl" when {

    "getting a business partner record" must {

      val bprRequest = BprRequest(Left(Organisation(SAUTR(""))))

      def expectedBpr(address: Address, organisationName: Option[String]) = BusinessPartnerRecord(
        Some("email"),
        address,
        "1234567890",
        organisationName
      )

      def json(addressBody: String, organisationName: Option[String]) =
        Json.parse(s"""
           |{
           |  "individual" : {
           |    "firstName"   : "forename",
           |    "lastName"    : "surname",
           |    "dateOfBirth" : "2000-01-02"
           |  },
           |  "contactDetails" : {
           |    "emailAddress" : "email"
           |  },
           |  "sapNumber" : "1234567890",
           |  ${organisationName.map(n => s""""organisation" : { "name" : "$n" },""").getOrElse("")}
           |  $addressBody
           |}
           |""".stripMargin)

      "return an error" when {

        def testError(response: => Either[Error, HttpResponse]) = {
          mockGetBPR(bprRequest)(response)

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
          testError(Left(Error(new Exception("Oh no!"))))
        }

        "the call comes back with status 200 and valid JSON but a postcode cannot " +
          "be found for a uk address" in {
          val body = json(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "countryCode"  : "GB"
              |  }
              |""".stripMargin,
            None
          )

          testError(Right(HttpResponse(200, Some(body))))
        }

      }

      "return a BPR" when {

        "the call comes back with status 200 with valid JSON and a valid UK address" in {
          val body = json(
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
            None
          )

          mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))

          val expectedAddress = UkAddress("line1", Some("line2"), Some("line3"), Some("line4"), "postcode")
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            expectedBpr(expectedAddress, None)
          )
        }

        "the call comes back with status 200 with valid JSON and a valid non-UK address" in {
          val body = json(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "countryCode"  : "HK"
              |  }
              |""".stripMargin,
            None
          )

          mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))

          val expectedAddress = NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, "HK")
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            expectedBpr(expectedAddress, None)
          )
        }

        "the call comes back with status 200 with valid JSON and an organisation name" in {
          val body = json(
            """
              |"address" : {
              |    "addressLine1" : "line1",
              |    "addressLine2" : "line2",
              |    "addressLine3" : "line3",
              |    "addressLine4" : "line4",
              |    "countryCode"  : "HK"
              |  }
              |""".stripMargin,
            Some("organisation")
          )

          mockGetBPR(bprRequest)(Right(HttpResponse(200, Some(body))))

          val expectedAddress = NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, "HK")
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            expectedBpr(expectedAddress, Some("organisation"))
          )
        }

      }

    }

  }

}
