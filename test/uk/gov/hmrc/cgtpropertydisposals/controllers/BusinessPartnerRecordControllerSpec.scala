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

import akka.stream.Materializer
import cats.data.EitherT
import org.scalacheck.ScalacheckShapeless._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse, Error, sample}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class BusinessPartnerRecordControllerSpec extends ControllerSpec {

  val bprService = mock[BusinessPartnerRecordService]

  override val overrideBindings: List[GuiceableModule] = List(bind[BusinessPartnerRecordService].toInstance(bprService))

  def mockBprService(expectedBprRequest: BusinessPartnerRecordRequest)(
    result: Either[Error, BusinessPartnerRecordResponse]
  ) =
    (bprService
      .getBusinessPartnerRecord(_: BusinessPartnerRecordRequest)(_: HeaderCarrier))
      .expects(expectedBprRequest, *)
      .returning(EitherT(Future.successful(result)))

  lazy val controller = instanceOf[BusinessPartnerRecordController]

  implicit lazy val mat: Materializer = fakeApplication.materializer

  def fakeRequestWithJsonBody(body: JsValue) = FakeRequest().withHeaders(CONTENT_TYPE -> JSON).withBody(body)

  "BusinessPartnerRecordController" when {

    val bpr = sample[BusinessPartnerRecord]

    val bprRequest = sample[BusinessPartnerRecordRequest]

    "handling requests to get BPR's" must {

      "return a BPR response if one is returned" in {
        List(
          BusinessPartnerRecordResponse(Some(bpr)),
          BusinessPartnerRecordResponse(None)
        ).foreach{ bprResponse =>
          mockBprService(bprRequest)(Right(bprResponse))

          val result = controller.getBusinessPartnerRecord()(fakeRequestWithJsonBody(Json.toJson(bprRequest)))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(bprResponse)
        }

      }

      "return an error" when {

        "there is an error getting a BPR" in {
          List(
            Error(new Exception("oh no!")),
            Error("oh no!")
          ).foreach { e =>
            mockBprService(bprRequest)(Left(e))

            val result = controller.getBusinessPartnerRecord()(fakeRequestWithJsonBody(Json.toJson(bprRequest)))
            status(result) shouldBe INTERNAL_SERVER_ERROR
          }

        }

        "the JSON in the body cannot be parsed" in {
          val result = controller.getBusinessPartnerRecord()(fakeRequestWithJsonBody(JsString("hello")))
          status(result) shouldBe BAD_REQUEST
        }


      }



    }

  }

}
