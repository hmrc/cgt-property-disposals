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
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanResultRepositorySpec extends WordSpec with Matchers with MongoSupport {

  val repository = new DefaultUpscanCallBackRepository(reactiveMongoComponent)
  val ts         = java.time.Instant.ofEpochSecond(1000)

  val cb = sample[UpscanCallBack]

  "Upscan Call Back Repository" when {
    "inserting" should {
      "create a new record" in {
        await(repository.insert(cb).value) shouldBe Right(())
      }
    }

    "counting" should {
      "return number of file descriptors" in {
        await(repository.insert(cb).value)              shouldBe Right(())
        await(repository.insert(cb).value)              shouldBe Right(())
        await(repository.insert(cb).value)              shouldBe Right(())
        await(repository.count(cb.draftReturnId).value) shouldBe Right(3)
      }
    }

    "getting all" should {
      "return all the call back events for a cgt reference" in {
        await(repository.insert(cb).value)               shouldBe Right(())
        await(repository.insert(cb).value)               shouldBe Right(())
        await(repository.insert(cb).value)               shouldBe Right(())
        await(repository.getAll(cb.draftReturnId).value) shouldBe Right(List(cb, cb, cb))
      }
    }
  }
}
