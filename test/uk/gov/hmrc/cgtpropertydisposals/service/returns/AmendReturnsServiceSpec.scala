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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.OnboardingGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.SubmitReturnGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.*
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.AmendReturnsRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AmendReturnsServiceSpec extends AnyWordSpec with Matchers with MongoSupport {

  private val mockAmendReturnsRepo = mock[AmendReturnsRepository]

  val returnsService = new DefaultAmendReturnsService(mockAmendReturnsRepo)

  implicit val hc: HeaderCarrier   = HeaderCarrier()
  implicit val request: Request[?] = FakeRequest()

  private def mockGetAmendReturnList(
    cgtReference: CgtReference
  )(result: Either[Error, List[SubmitReturnWrapper]]) =
    when(
      mockAmendReturnsRepo
        .fetch(cgtReference)
    ).thenReturn(EitherT.fromEither[Future](result))

  private def mockSaveAmendReturnList(
    submitReturnRequest: SubmitReturnRequest
  )(result: Either[Error, Unit]) =
    when(
      mockAmendReturnsRepo
        .save(submitReturnRequest)
    ).thenReturn(EitherT.fromEither[Future](result))

  "AmendReturnService" when {
    "handling saving amend returns" should {
      "handle successful saves to the database" in {
        val cgtReference        = sample[CgtReference]
        val subscribedDetails   = sample[SubscribedDetails].copy(cgtReference = cgtReference)
        val submitReturnRequest = sample[SubmitReturnRequest].copy(subscribedDetails = subscribedDetails)
        mockSaveAmendReturnList(submitReturnRequest)(Right(()))

        await(returnsService.saveAmendedReturn(submitReturnRequest).value) shouldBe Right(())
      }
    }

    "handling fetching amend returns" should {
      "handle successful gets from the database" in {
        val cgtReference = sample[CgtReference]
        mockGetAmendReturnList(cgtReference)(Right(List.empty))

        await(returnsService.getAmendedReturn(cgtReference).value) shouldBe Right(List.empty)
      }
    }
  }
}
