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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.EitherT
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.{BprRequest, BusinessPartnerRecord, DateOfBirth, Error, NINO, Name}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class BusinessPartnerRecordControllerSpec extends ControllerSpec {

  val bprService = mock[BusinessPartnerRecordService]

  override val overrideBindings: List[GuiceableModule] = List(bind[BusinessPartnerRecordService].toInstance(bprService))

  def mockBprService(expectedNino: NINO, expectedName: Name, expectedDateOfBirth: DateOfBirth)(
    result: Either[Error, BusinessPartnerRecord]
  ) =
    (bprService
      .getBusinessPartnerRecord(_: NINO, _: Name, _: DateOfBirth)(_: HeaderCarrier))
      .expects(expectedNino, expectedName, expectedDateOfBirth, *)
      .returning(EitherT(Future.successful(result)))

  lazy val controller = instanceOf[BusinessPartnerRecordController]

  val nino           = NINO("AB123456C")
  val name           = Name("forename", "surname")
  val dateOfBirth    = DateOfBirth(LocalDate.parse("2000-04-12", DateTimeFormatter.ISO_LOCAL_DATE))
  val badDateOfBirth = DateOfBirth(LocalDate.parse("20000411", DateTimeFormatter.BASIC_ISO_DATE))

  "BusinessPartnerRecordController" when {

    implicit val materializer: ActorMaterializer = ActorMaterializer()(ActorSystem())

    "handling requests to get BPS's" must {

      "return a BPR if one can be found" in {
        val bpr = BusinessPartnerRecord(
          DateOfBirth(LocalDate.ofEpochDay(0L)),
          Some("email"),
          UkAddress("line1", Some("line2"), Some("line3"), None, "postcode"),
          "sap"
        )

        mockBprService(nino, name, dateOfBirth)(Right(bpr))

        val payload =
          Json.toJson[BprRequest](BprRequest(nino.value, name.firstName, name.lastName, dateOfBirth.value))

        val result = controller.getBusinessPartnerRecord(
          FakeRequest("POST", "/business-partner-record").withHeaders(CONTENT_TYPE -> JSON).withBody(payload)
        )
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(bpr)
      }

      "return a bad request if a date with the incorrect format is sent in the payload" in {
        val payload = Json.parse(
          s"""
             | {
             |   "nino" : "AB123456C",
             |   "fname" : "forename",
             |   "lname" : "surname",
             |   "dateofBirth" : "12-100-9999"
             |   }
             |""".stripMargin
        )

        val result = controller.getBusinessPartnerRecord(
          FakeRequest("POST", "/business-partner-record").withHeaders(CONTENT_TYPE -> JSON).withBody(payload)
        )
        status(result) shouldBe BAD_REQUEST
      }

      "return an error" when {

        "there is an error getting a BPR" in {
          List(
            Error(new Exception("oh no!")),
            Error("oh no!")
          ).foreach { e =>
            mockBprService(nino, name, dateOfBirth)(Left(e))

            val requestPayload =
              Json.toJson[BprRequest](BprRequest(nino.value, name.firstName, name.lastName, dateOfBirth.value))
            val result = controller.getBusinessPartnerRecord()(
              FakeRequest("POST", "/business-partner-record")
                .withHeaders(CONTENT_TYPE -> JSON)
                .withBody(requestPayload)
            )
            status(result) shouldBe INTERNAL_SERVER_ERROR
          }

        }

      }

    }

  }

}
