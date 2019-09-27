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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import akka.stream.Materializer
import cats.data.EitherT
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, Json}
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.BprRequest.{Individual, Organisation}
import uk.gov.hmrc.cgtpropertydisposals.models.{BprRequest, BusinessPartnerRecord, DateOfBirth, Error, NINO, Name, SAUTR}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.cgtpropertydisposals.{Example, Fake}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessPartnerRecordControllerSpec extends ControllerSpec {

  val bprService = mock[BusinessPartnerRecordService]

  override val overrideBindings: List[GuiceableModule] = List(bind[BusinessPartnerRecordService].toInstance(bprService))
  val headerCarrier                                    = HeaderCarrier()

  def mockBprService(expectedBprRequest: BprRequest)(
    result: Either[Error, BusinessPartnerRecord]
  ) =
    (bprService
      .getBusinessPartnerRecord(_: BprRequest)(_: HeaderCarrier))
      .expects(expectedBprRequest, *)
      .returning(EitherT(Future.successful(result)))

  lazy val controller = instanceOf[BusinessPartnerRecordController]

  implicit lazy val mat: Materializer = fakeApplication.materializer

  "BusinessPartnerRecordController" when {
    val request =
      new AuthenticatedRequest(
        Example.user,
        LocalDateTime.now(),
        headerCarrier,
        FakeRequest()
      )

    val bpr = BusinessPartnerRecord(
      Some("email"),
      UkAddress("line1", Some("line2"), Some("line3"), None, "postcode"),
      "sap",
      None
    )

    "handling requests to get BPR's for sautrs" must {
      val sautr = SAUTR("satur")

      val expectedBprRequest = BprRequest(Left(Organisation(sautr)))

      "return a BPR if one can be found" in {
        mockBprService(expectedBprRequest)(Right(bpr))

        val controller = new BusinessPartnerRecordController(
          authenticate = Fake.loggedAs(Example.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
          bprService   = bprService,
          cc           = Helpers.stubControllerComponents()
        )
        val result = controller.getBusinessPartnerRecordFromSautr(sautr.value)(request)
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

            val controller = new BusinessPartnerRecordController(
              authenticate = Fake.loggedAs(Example.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
              bprService   = bprService,
              cc           = Helpers.stubControllerComponents()
            )

            val result = controller.getBusinessPartnerRecordFromSautr(sautr.value)(request)
            status(result) shouldBe INTERNAL_SERVER_ERROR
          }

        }
      }

    }

    "handling requests to get BPR's for ninos" must {

      val nino        = NINO("AB123456C")
      val name        = Name("forename", "surname")
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
      val expectedBprRequest = BprRequest(Right(Individual(nino, name, dateOfBirth)))

      "return a BPR if one can be found" in {
        mockBprService(expectedBprRequest)(Right(bpr))

        val controller = new BusinessPartnerRecordController(
          authenticate = Fake.loggedAs(Example.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
          bprService   = bprService,
          cc           = Helpers.stubControllerComponents()
        )

        val result = controller.getBusinessPartnerRecordFromNino(nino.value)(
          request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(validPayload)
        )
        status(result)        shouldBe OK
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

        val controller = new BusinessPartnerRecordController(
          authenticate = Fake.loggedAs(Example.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
          bprService   = bprService,
          cc           = Helpers.stubControllerComponents()
        )

        val result = controller.getBusinessPartnerRecordFromNino(nino.value)(
          request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(payload)
        )
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

        val controller = new BusinessPartnerRecordController(
          authenticate = Fake.loggedAs(Example.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
          bprService   = bprService,
          cc           = Helpers.stubControllerComponents()
        )

        val result = controller.getBusinessPartnerRecordFromNino(nino.value)(
          request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(payload)
        )
        status(result) shouldBe BAD_REQUEST
      }

      "return an error" when {

        "there is an error getting a BPR" in {
          List(
            Error(new Exception("oh no!")),
            Error("oh no!")
          ).foreach { e =>
            mockBprService(expectedBprRequest)(Left(e))

            val controller = new BusinessPartnerRecordController(
              authenticate = Fake.loggedAs(Example.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
              bprService   = bprService,
              cc           = Helpers.stubControllerComponents()
            )

            val result = controller.getBusinessPartnerRecordFromNino(nino.value)(
              request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(validPayload)
            )
            status(result) shouldBe INTERNAL_SERVER_ERROR
          }

        }

      }

    }

  }

}
