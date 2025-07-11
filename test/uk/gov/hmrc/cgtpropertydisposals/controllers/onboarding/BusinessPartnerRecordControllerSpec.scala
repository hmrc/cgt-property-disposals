/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.Headers
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.cgtpropertydisposals.models.generators.BusinessPartnerRecordGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.OnboardingGen.given

class BusinessPartnerRecordControllerSpec extends ControllerSpec {
  private val bprService = mock[BusinessPartnerRecordService]

  private val headerCarrier = HeaderCarrier()

  private def mockBprService(expectedBprRequest: BusinessPartnerRecordRequest)(
    result: Either[Error, BusinessPartnerRecordResponse]
  ) =
    when(
      bprService
        .getBusinessPartnerRecord(ArgumentMatchers.eq(expectedBprRequest))(any(), any())
    ).thenReturn(EitherT(Future.successful(result)))

  val request =
    new AuthenticatedRequest(
      Fake.user,
      LocalDateTime.now(),
      headerCarrier,
      FakeRequest()
    )

  private def fakeRequestWithJsonBody(body: JsValue) =
    request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new BusinessPartnerRecordController(
    authenticate = Fake.login(Fake.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
    bprService = bprService,
    cc = Helpers.stubControllerComponents()
  )

  "BusinessPartnerRecordController" when {
    val bpr = sample[BusinessPartnerRecord]

    val cgtReference = sample[CgtReference]

    val bprRequest = sample[BusinessPartnerRecordRequest]

    "handling requests to get BPR's" must {
      "return a BPR response if one is returned" in {
        List(
          BusinessPartnerRecordResponse(Some(bpr), Some(cgtReference), None),
          BusinessPartnerRecordResponse(Some(bpr), None, None),
          BusinessPartnerRecordResponse(None, None, Some(sample[SubscribedDetails])),
          BusinessPartnerRecordResponse(None, None, None)
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
