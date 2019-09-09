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

package uk.gov.hmrc.cgtpropertydisposals.controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import akka.stream.Materializer
import cats.data.EitherT
import play.api.mvc.Result
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.BprRequest.{Individual, Organisation}
import uk.gov.hmrc.cgtpropertydisposals.models.{BprRequest, BusinessPartnerRecord, DateOfBirth, Error, NINO, Name, SAUTR}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class BusinessPartnerRecordControllerSpec extends ControllerSpec {

  val bprService = mock[BusinessPartnerRecordService]

  override val overrideBindings: List[GuiceableModule] = List(bind[BusinessPartnerRecordService].toInstance(bprService))

  def mockBprService(expectedBprRequest: BprRequest)(
    result: Either[Error, BusinessPartnerRecord]
  ) =
    (bprService
      .getBusinessPartnerRecord(_: BprRequest)(_: HeaderCarrier))
      .expects(expectedBprRequest, *)
      .returning(EitherT(Future.successful(result)))

  lazy val controller = instanceOf[BusinessPartnerRecordController]

  implicit lazy val mat: Materializer = fakeApplication.materializer

  def fakeRequestWithJsonBody(body: JsValue) = FakeRequest().withHeaders(CONTENT_TYPE -> JSON).withBody(body)

  "BusinessPartnerRecordController" when {

    val bpr = BusinessPartnerRecord(
      Some("email"),
      UkAddress("line1", Some("line2"), Some("line3"), None, "postcode"),
      "sap",
      None
    )

    "handling requests to get BPR's for organisations" must {
      val sautr = SAUTR("satur")

      val expectedBprRequest = BprRequest(Left(Organisation(sautr)))

      "return a BPR if one can be found" in {
        mockBprService(expectedBprRequest)(Right(bpr))

        val result = controller.getBusinessPartnerRecordFromSautr(sautr.value)(fakeRequestWithJsonBody(JsNull))
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(bpr)
      }

      "return an error" when {

        "there is an error getting a BPR" in {
          List(
            Error(new Exception("oh no!")),
            Error("oh no!")
          ).foreach { e =>
            mockBprService(expectedBprRequest)(Left(e))

            val result = controller.getBusinessPartnerRecordFromSautr(sautr.value)(fakeRequestWithJsonBody(JsNull))
            status(result) shouldBe INTERNAL_SERVER_ERROR
          }

        }
      }

    }

    "handling requests to get BPR's for individuals" when {

      val name = Name("forename", "surname")
      val dateOfBirth = DateOfBirth(LocalDate.of(2000, 4, 12))
      val validPayload = Json.parse(
        s"""
           | {
           |   "forename" : "${name.firstName}",
           |   "surname" : "${name.lastName}",
           |   "dateOfBirth" : "${dateOfBirth.value.format(DateTimeFormatter.ISO_DATE)}"
           |   }
           |""".stripMargin
      )

      def commonBehaviour(performAction: JsValue => Future[Result],
                          expectedBprRequest: BprRequest): Unit = {

        "return a BPR if one can be found" in {
          mockBprService(expectedBprRequest)(Right(bpr))

          val result = performAction(validPayload)
          status(result) shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(bpr)
        }

        "return a bad request if a date with the incorrect format is sent in the payload" in {
          val payload = Json.parse(
            s"""
               | {
               |   "forename" : "forename",
               |   "surname" : "surname",
               |   "dateOfBirth" : "12-100-9999"
               |   }
               |""".stripMargin
          )

          val result = performAction(payload)
          status(result) shouldBe BAD_REQUEST
        }

        "return a bad request if payload has incorrect structure" in {
          val payload = Json.parse(
            s"""
               | {
               |   "bino" : "AB123456C",
               |   "lname" : "surname"
               |   }
               |""".stripMargin
          )

          val result = performAction(payload)
          status(result) shouldBe BAD_REQUEST
        }

        "return an error" when {

          "there is an error getting a BPR" in {
            List(
              Error(new Exception("oh no!")),
              Error("oh no!")
            ).foreach { e =>
              mockBprService(expectedBprRequest)(Left(e))

              val result = performAction(validPayload)
              status(result) shouldBe INTERNAL_SERVER_ERROR
            }

          }

        }
      }

      "passed an SAUTR" must {
        val sautr = SAUTR("1234567890")
        val expectedBprRequest = BprRequest(Right(Individual(Left(sautr), name, Some(dateOfBirth))))

        behave like commonBehaviour(
          json => controller.getBusinessPartnerRecordFromSautr(sautr.value)(fakeRequestWithJsonBody(json)),
          expectedBprRequest
        )

      }

      "passed a NINO" must {
        val nino = NINO("AB123456C")
        val expectedBprRequest = BprRequest(Right(Individual(Right(nino), name, Some(dateOfBirth))))

        behave like commonBehaviour(
          json => controller.getBusinessPartnerRecordFromNino(nino.value)(fakeRequestWithJsonBody(json)),
          expectedBprRequest
        )

      }
    }

  }

}
