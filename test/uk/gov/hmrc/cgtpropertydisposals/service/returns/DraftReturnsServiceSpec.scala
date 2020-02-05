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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import cats.instances.future._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DraftReturnsRepository
import uk.gov.hmrc.http.HeaderCarrier
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference

import scala.concurrent.Future

class DraftReturnsServiceSpec extends WordSpec with Matchers with MockFactory {

  val draftReturnRepository = mock[DraftReturnsRepository]
  val draftReturnsService   = new DefaultDraftReturnsService(draftReturnRepository)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def mockStoreDraftReturn(draftReturn: DraftReturn)(response: Either[Error, Unit]) =
    (draftReturnRepository
      .save(_: DraftReturn))
      .expects(draftReturn)
      .returning(EitherT.fromEither[Future](response))

  def mockGetDraftReturn(cgtReference: CgtReference)(response: Either[Error, List[DraftReturn]]) =
    (draftReturnRepository
      .fetch(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT.fromEither[Future](response))

  "DraftReturnsRepository" when {
    "Store" should {
      "create a new draft return successfully" in {
        val draftReturn = sample[DraftReturn]
        mockStoreDraftReturn(draftReturn)(Right(()))
        await(draftReturnsService.saveDraftReturn(draftReturn).value) shouldBe Right(())
      }

      "fail to create a new draft return" in {
        val draftReturn = sample[DraftReturn]
        mockStoreDraftReturn(draftReturn)(Left(Error("Could not store draft return: $error")))
        await(draftReturnsService.saveDraftReturn(draftReturn).value).isLeft shouldBe true
      }
    }

    "Retrieve" should {
      "return draft returns successfully" in {
        val cgtReference = sample[CgtReference]
        val draftReturn  = sample[DraftReturn]

        mockGetDraftReturn(cgtReference)(Right(List(draftReturn)))
        await(draftReturnsService.getDraftReturn(cgtReference).value) shouldBe (Right(List(draftReturn)))
      }
    }
  }
}
