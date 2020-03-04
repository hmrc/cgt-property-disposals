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
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{FileDescriptorId, UpscanFileDescriptor}
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanFileDescriptor.UpscanFileDescriptorStatus.UPLOADED

class UpscanFileDescriptorRepositorySpec extends WordSpec with Matchers with MongoSupport {

  val repository = new DefaultUpscanFileDescriptorRepository(reactiveMongoComponent)

  val fd = sample[UpscanFileDescriptor]

  "Upscan File Descriptor Repository" when {
    "inserting" should {
      "create a new record" in {
        await(repository.insert(fd).value) shouldBe Right(())
      }
    }

    "counting" should {
      "return number of file descriptors" in {
        await(repository.insert(fd).value)             shouldBe Right(())
        await(repository.insert(fd).value)             shouldBe Right(())
        await(repository.insert(fd).value)             shouldBe Right(())
        await(repository.count(fd.cgtReference).value) shouldBe Right(3)
      }
    }

    "updating upscan upload status" should {
      "return true if the update was successful" in {
        await(repository.insert(fd).value) shouldBe Right(())
        await(repository.updateUpscanUploadStatus(fd.copy(status = UPLOADED)).value) shouldBe Right(true)
      }
    }

    "getting all upscan file descriptors" should {
      "return two if there are only two in the repo" in {
        await(repository.insert(fd).value)                               shouldBe Right(())
        await(repository.insert(fd).value)                               shouldBe Right(())
        await(repository.getAll(fd.cgtReference).value).map(s => s.size) shouldBe Right(2)
      }
    }

    "get a upscan file descriptor" should {
      "return one if it exists" in {
        await(repository.insert(fd).value)                    shouldBe Right(())
        await(repository.get(FileDescriptorId(fd.key)).value) shouldBe Right(Some(fd))
      }
    }

  }
}
