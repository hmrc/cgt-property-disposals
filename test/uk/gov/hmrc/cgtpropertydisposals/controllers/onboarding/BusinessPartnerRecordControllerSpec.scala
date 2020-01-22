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

package uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding

import java.time.LocalDateTime

import akka.stream.Materializer
import cats.data.EitherT
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BusinessPartnerRecordControllerSpec extends ControllerSpec {

  val bprService = mock[BusinessPartnerRecordService]

  override val overrideBindings: List[GuiceableModule] = List(bind[BusinessPartnerRecordService].toInstance(bprService))
  val headerCarrier                                    = HeaderCarrier()

  def mockBprService(expectedBprRequest: BusinessPartnerRecordRequest)(
    result: Either[Error, BusinessPartnerRecordResponse]
  ) =
    (bprService
      .getBusinessPartnerRecord(_: BusinessPartnerRecordRequest)(_: HeaderCarrier))
      .expects(expectedBprRequest, *)
      .returning(EitherT(Future.successful(result)))

  implicit lazy val mat: Materializer = fakeApplication.materializer

  val request =
    new AuthenticatedRequest(
      Fake.user,
      LocalDateTime.now(),
      headerCarrier,
      FakeRequest()
    )

  def fakeRequestWithJsonBody(body: JsValue) = request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new BusinessPartnerRecordController(
    authenticate = Fake.login(Fake.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
    bprService   = bprService,
    cc           = Helpers.stubControllerComponents()
  )

  "BusinessPartnerRecordController" when {

    val bpr = sample[BusinessPartnerRecord]

    val cgtReference = sample[CgtReference]

    val bprRequest = sample[BusinessPartnerRecordRequest]

    "handling requests to get BPR's" must {

      "return a BPR response if one is returned" in {

        List(
          BusinessPartnerRecordResponse(Some(bpr), Some(cgtReference)),
          BusinessPartnerRecordResponse(Some(bpr), None),
          BusinessPartnerRecordResponse(None, None)
        ).foreach { bprResponse =>
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
