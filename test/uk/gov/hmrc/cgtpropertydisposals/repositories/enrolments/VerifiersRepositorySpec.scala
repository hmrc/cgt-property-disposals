/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.repositories.enrolments

import java.time.LocalDateTime

import org.scalacheck.Arbitrary
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest

import scala.concurrent.ExecutionContext.Implicits.global

class VerifiersRepositorySpec extends AnyWordSpec with Matchers with MongoSupport {

  val repository = new DefaultVerifiersRepository(reactiveMongoComponent)

  implicit val arbLocalDateTime: Arbitrary[LocalDateTime] =
    Arbitrary((LocalDateTime.now()))

  val verifierDetails = sample[UpdateVerifiersRequest]

  "The Update Verifiers repository" when {
    "inserting" should {
      "create a new record" in {
        await(repository.insert(verifierDetails).value) shouldBe Right(())
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        await(repository.insert(verifierDetails).value)       shouldBe Right(())
        await(repository.get(verifierDetails.ggCredId).value) shouldBe (Right(Some(verifierDetails)))
      }
      "return none if the record does not exist" in {
        await(repository.get("this-gg-cred-id-does-not-exist").value) shouldBe (Right(None))
      }
    }

    "deleting" should {
      "return a count of one when deleting a unique tax enrolment record" in {
        await(repository.insert(verifierDetails).value)
        await(repository.delete(verifierDetails.ggCredId).value) shouldBe (Right(1))
      }
      "return a count of zero when the tax enrolment record does not exist" in {
        await(repository.delete("this-gg-cred-id-does-not-exist").value) shouldBe (Right(0))
      }
    }
  }
}
