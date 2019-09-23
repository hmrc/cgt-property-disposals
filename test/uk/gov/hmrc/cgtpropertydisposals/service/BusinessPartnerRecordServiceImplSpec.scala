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
import uk.gov.hmrc.cgtpropertydisposals.models.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, BusinessPartnerRecord, BusinessPartnerRecordRequest, Country, Error, Name, TrustName, sample}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessPartnerRecordServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector: BusinessPartnerRecordConnector = mock[BusinessPartnerRecordConnector]

  val nonIsoCountryCode = "XZ"

  val config = Configuration(ConfigFactory.parseString(
    s"""
      |des.non-iso-country-codes = ["$nonIsoCountryCode"]
      |""".stripMargin))

  val service = new BusinessPartnerRecordServiceImpl(mockConnector, config)

  def mockGetBPR(bprRequest: BusinessPartnerRecordRequest)(response: Either[Error, HttpResponse]) =
    (mockConnector
      .getBusinessPartnerRecord(_: BusinessPartnerRecordRequest)(_: HeaderCarrier))
      .expects(bprRequest, *)
      .returning(EitherT(Future.successful(response)))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val bprRequest = sample[BusinessPartnerRecordRequest]

  val (name, trustName) = sample[Name] -> sample[TrustName]



  "The BusinessPartnerRecordServiceImpl" when {

    "getting a business partner record" must {

      def expectedBpr(address: Address, name: Either[TrustName, Name]) =
        BusinessPartnerRecord(Some("email"), address, "1234567890", name)

      def responseJson(addressBody: String, organisationName: Option[TrustName], individualName: Option[Name]) =
        Json.parse(s"""
           |{
           |  ${individualName.map(n => s""""individual":{"firstName":"${n.firstName}","lastName":"${n.lastName}"},""").getOrElse("")}
           |  "contactDetails" : {
           |    "emailAddress" : "email"
           |  },
           |  "sapNumber" : "1234567890",
           |  ${organisationName.map(trustName => s""""organisation":{"name":"${trustName.value}"},""").getOrElse("")}
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

      "return a BPR" when {

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

          val expectedAddress = UkAddress("line1", Some("line2"), Some("line3"), Some("line4"), "postcode")
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            expectedBpr(expectedAddress, Right(name))
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

          val expectedAddress = NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country("HK", Some("Hong Kong")))
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            expectedBpr(expectedAddress, Left(trustName))
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

          val expectedAddress = NonUkAddress("line1", Some("line2"), Some("line3"), Some("line4"), None, Country(nonIsoCountryCode, None))
          await(service.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(
            expectedBpr(expectedAddress, Left(trustName))
          )
        }


      }

    }

  }

}
