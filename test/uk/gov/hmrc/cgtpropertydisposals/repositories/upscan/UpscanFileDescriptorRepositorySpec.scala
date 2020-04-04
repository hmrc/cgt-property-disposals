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

package uk.gov.hmrc.cgtpropertydisposals.repositories.upscan

import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanFileDescriptor.UpscanFileDescriptorStatus.UPLOADED
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UpscanFileDescriptor, UpscanInitiateReference}
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanFileDescriptorRepositorySpec extends WordSpec with Matchers with MongoSupport {

  val repository = new DefaultUpscanFileDescriptorRepository(reactiveMongoComponent)

  val dr = sample[DraftReturnId]
  val fd = sample[UpscanFileDescriptor].copy(draftReturnId = dr)

  "Upscan File Descriptor Repository" when {
    "inserting" should {
      "create a new record" in {
        await(repository.insert(fd).value) shouldBe Right(())
      }
    }

    "updating upscan upload status" should {
      "return true if the update was successful" in {
        await(repository.insert(fd).value) shouldBe Right(())
        await(repository.updateUpscanUploadStatus(fd.copy(status = UPLOADED)).value) shouldBe Right(true)
      }
    }

    "get a upscan file descriptor" should {
      "return one if it exists" in {
        await(repository.insert(fd).value) shouldBe Right(())
        await(repository.get(dr, UpscanInitiateReference(fd.upscanInitiateReference.value)).value) shouldBe Right(
          Some(fd)
        )
      }
    }

  }
}
