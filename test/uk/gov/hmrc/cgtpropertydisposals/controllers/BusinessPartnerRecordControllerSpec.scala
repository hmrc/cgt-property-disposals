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

import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, BusinessPartnerRecord, DateOfBirth, Error, NINO}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class BusinessPartnerRecordControllerSpec extends ControllerSpec {

  val bprService = mock[BusinessPartnerRecordService]

  override val overrideBindings: List[GuiceableModule] = List(bind[BusinessPartnerRecordService].toInstance(bprService))

  def mockBprService(expectedNino: NINO)(result: Future[Either[Error, BusinessPartnerRecord]]) =
    (bprService.getBusinessPartnerRecord(_: NINO)(_: HeaderCarrier))
      .expects(expectedNino, *)
      .returning(result)

  lazy val controller = instanceOf[BusinessPartnerRecordController]

  val nino = NINO("AB123456C")

  "BusinessPartnerRecordController" when {

    "handling requests to get BPS's" must {

      "return a BPR if one can be found" in {
        val bpr = BusinessPartnerRecord(
          "name", "surname", DateOfBirth(LocalDate.ofEpochDay(0L)),
                             Some("email"), UkAddress("line1", Some("line2"), Some("line3"), None, "postcode"),
          "sap"
        )

        mockBprService(nino)(Future.successful(Right(bpr)))

        val result = controller.getBusinessPartnerRecord(nino)(FakeRequest())
        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(bpr)
      }

      "return an error" when {

        "there is an error getting a BPR" in {
          List(
            Error(new Exception("oh no!")),
            Error("oh no!")
          ).foreach { e =>
              mockBprService(nino)(Future.successful(Left(e)))

              val result = controller.getBusinessPartnerRecord(nino)(FakeRequest())
              status(result) shouldBe INTERNAL_SERVER_ERROR
            }

        }

      }

    }

  }

}
