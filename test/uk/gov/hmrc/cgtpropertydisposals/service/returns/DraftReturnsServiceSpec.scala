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
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DraftReturnsRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DraftReturnsServiceSpec extends WordSpec with Matchers with MockFactory {

  val draftReturnRepository = mock[DraftReturnsRepository]
  val draftReturnsService   = new DefaultDraftReturnsService(draftReturnRepository)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def mockStoreDraftReturn(draftReturn: DraftReturn, cgtReference: CgtReference)(response: Either[Error, Unit]) =
    (draftReturnRepository
      .save(_: DraftReturn, _: CgtReference))
      .expects(draftReturn, cgtReference)
      .returning(EitherT.fromEither[Future](response))

  def mockGetDraftReturn(cgtReference: CgtReference)(response: Either[Error, List[DraftReturn]]) =
    (draftReturnRepository
      .fetch(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT.fromEither[Future](response))

  "DraftReturnsRepository" when {
    "Store" should {
      val cgtReference = sample[CgtReference]

      "create a new draft return successfully" in {
        val draftReturn = sample[DraftReturn]
        mockStoreDraftReturn(draftReturn, cgtReference)(Right(()))
        await(draftReturnsService.saveDraftReturn(draftReturn, cgtReference).value) shouldBe Right(())
      }

      "fail to create a new draft return" in {
        val draftReturn = sample[DraftReturn]
        mockStoreDraftReturn(draftReturn, cgtReference)(Left(Error("Could not store draft return: $error")))
        await(draftReturnsService.saveDraftReturn(draftReturn, cgtReference).value).isLeft shouldBe true
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
