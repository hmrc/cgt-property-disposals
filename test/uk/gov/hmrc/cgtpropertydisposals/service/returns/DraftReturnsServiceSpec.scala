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
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DraftReturnGen.multipleDisposalDraftReturnGen
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.cgtReferenceGen
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DraftReturnsRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DraftReturnsServiceSpec extends AnyWordSpec with Matchers {

  private val draftReturnRepository = mock[DraftReturnsRepository]
  val draftReturnsService           = new DefaultDraftReturnsService(draftReturnRepository)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private def mockStoreDraftReturn(draftReturn: DraftReturn, cgtReference: CgtReference)(
    response: Either[Error, Unit]
  ) =
    when(
      draftReturnRepository
        .save(draftReturn, cgtReference)
    ).thenReturn(EitherT.fromEither[Future](response))

  private def mockGetDraftReturn(cgtReference: CgtReference)(response: Either[Error, List[DraftReturn]]) =
    when(
      draftReturnRepository
        .fetch(cgtReference)
    ).thenReturn(EitherT.fromEither[Future](response))

  private def mockDeleteDraftReturns(draftReturnIds: List[UUID])(response: Either[Error, Unit]) =
    when(
      draftReturnRepository
        .deleteAll(draftReturnIds)
    ).thenReturn(EitherT.fromEither[Future](response))

  private def mockDeleteADraftReturn(cgtReference: CgtReference)(response: Either[Error, Unit]) =
    when(
      draftReturnRepository
        .delete(cgtReference)
    ).thenReturn(EitherT.fromEither[Future](response))

  "DraftReturnsRepository" when {
    "storing draft returns" should {
      val cgtReference = sample[CgtReference]
      val draftReturn  = sample[DraftReturn]

      "return a successful response if operation was successful" in {
        mockStoreDraftReturn(draftReturn, cgtReference)(Right(()))
        await(draftReturnsService.saveDraftReturn(draftReturn, cgtReference).value) shouldBe Right(())
      }

      "return an unsuccessful response if the operation was no successful" in {
        mockStoreDraftReturn(draftReturn, cgtReference)(Left(Error("Could not store draft return: $error")))
        await(draftReturnsService.saveDraftReturn(draftReturn, cgtReference).value).isLeft shouldBe true
      }
    }

    "getting draft returns" should {
      val cgtReference = sample[CgtReference]
      val draftReturn  = sample[DraftReturn]

      "return a successful response if operation was successful" in {
        mockGetDraftReturn(cgtReference)(Right(List(draftReturn)))
        await(draftReturnsService.getDraftReturn(cgtReference).value) shouldBe Right(List(draftReturn))
      }

      "return an unsuccessful response if operation was not successful" in {
        mockGetDraftReturn(cgtReference)(Left(Error("")))
        await(draftReturnsService.getDraftReturn(cgtReference).value).isLeft shouldBe true
      }
    }

    "deleting draft returns" should {
      val draftReturnIds = List.fill(5)(UUID.randomUUID())

      "return a successful response if operation was successful" in {
        mockDeleteDraftReturns(draftReturnIds)(Right(()))
        await(draftReturnsService.deleteDraftReturns(draftReturnIds).value) shouldBe Right(())
      }

      "return an unsuccessful response if operation was not successful" in {
        mockDeleteDraftReturns(draftReturnIds)(Left(Error("")))
        await(draftReturnsService.deleteDraftReturns(draftReturnIds).value).isLeft shouldBe true
      }
    }

    "deleting a draft return" should {
      val cgtReference = CgtReference(UUID.randomUUID.toString)

      "return a successful response if operation was successful" in {
        mockDeleteADraftReturn(cgtReference)(Right(()))
        await(draftReturnsService.deleteDraftReturn(cgtReference).value) shouldBe Right(())
      }

      "return an unsuccessful response if operation was not successful" in {
        mockDeleteADraftReturn(cgtReference)(Left(Error("")))
        await(draftReturnsService.deleteDraftReturn(cgtReference).value).isLeft shouldBe true
      }
    }
  }
}
