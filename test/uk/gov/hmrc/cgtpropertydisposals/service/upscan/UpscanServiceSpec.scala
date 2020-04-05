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

package uk.gov.hmrc.cgtpropertydisposals.service.upscan

import java.util.concurrent.TimeUnit

import akka.util.Timeout
import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers.await
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UpscanReference, UpscanUpload}
import uk.gov.hmrc.cgtpropertydisposals.repositories.upscan.UpscanRepository

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class UpscanServiceSpec extends WordSpec with Matchers with MockFactory {

  implicit val timeout: Timeout                           = Timeout(FiniteDuration(5, TimeUnit.SECONDS))
  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
  val mockUpscanRepository                                = mock[UpscanRepository]
  val service                                             = new UpscanServiceImpl(mockUpscanRepository)

  def mockStoreUpscanUpload(upscanUpload: UpscanUpload)(
    response: Either[Error, Unit]
  ) =
    (mockUpscanRepository
      .insert(_: UpscanUpload))
      .expects(upscanUpload)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockReadUpscanUpload(draftReturnId: DraftReturnId, upscanReference: UpscanReference)(
    response: Either[Error, Option[UpscanUpload]]
  ) =
    (mockUpscanRepository
      .select(_: DraftReturnId, _: UpscanReference))
      .expects(draftReturnId, upscanReference)
      .returning(EitherT[Future, Error, Option[UpscanUpload]](Future.successful(response)))

  def mockUpdateUpscanUpload(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference,
    upscanUpload: UpscanUpload
  )(
    response: Either[Error, Unit]
  ) =
    (mockUpscanRepository
      .update(_: DraftReturnId, _: UpscanReference, _: UpscanUpload))
      .expects(draftReturnId, upscanReference, upscanUpload)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  val upscanUpload    = sample[UpscanUpload]
  val draftReturnId   = upscanUpload.draftReturnId
  val upscanReference = UpscanReference(upscanUpload.upscanUploadMeta.reference)

  "Upscan Service" when {

    "it receives a request to store an upscan upload" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockStoreUpscanUpload(upscanUpload)(Left(Error("Connection error")))
          await(service.storeUpscanUpload(upscanUpload).value).isLeft shouldBe true
        }
      }
      "return unit" when {
        "it successfully stores the data" in {
          mockStoreUpscanUpload(upscanUpload)(Right(()))
          await(service.storeUpscanUpload(upscanUpload).value) shouldBe Right(())
        }
      }
    }

    "it receives a request to read a upscan upload" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockReadUpscanUpload(draftReturnId, upscanReference)(Left(Error("Connection error")))
          await(service.readUpscanUpload(draftReturnId, upscanReference).value).isLeft shouldBe true
        }
      }
      "return some upscan upload" when {
        "it successfully stores the data" in {
          mockReadUpscanUpload(draftReturnId, upscanReference)(Right(Some(upscanUpload)))
          await(service.readUpscanUpload(draftReturnId, upscanReference).value) shouldBe Right(Some(upscanUpload))
        }
      }
    }

    "it receives a request to update an upscan upload" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockUpdateUpscanUpload(draftReturnId, upscanReference, upscanUpload)(Left(Error("Connection error")))
          await(service.updateUpscanUpload(draftReturnId, upscanReference, upscanUpload).value).isLeft shouldBe true
        }
      }
      "return some upscan upload" when {
        "it successfully stores the data" in {
          mockUpdateUpscanUpload(draftReturnId, upscanReference, upscanUpload)(Right(Some(upscanUpload)))
          await(service.updateUpscanUpload(draftReturnId, upscanReference, upscanUpload).value) shouldBe Right(())
        }
      }
    }
  }
}
