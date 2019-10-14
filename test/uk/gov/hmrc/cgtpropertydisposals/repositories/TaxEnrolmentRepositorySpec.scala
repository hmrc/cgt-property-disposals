/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.repositories

import java.time.LocalDateTime

import org.scalacheck.Arbitrary
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.{TaxEnrolmentRequest, sample}

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentRepositorySpec extends WordSpec with Matchers with MongoSupport {

  val repository = new DefaultTaxEnrolmentRepository(reactiveMongoComponent)

  implicit val arbLocalDateTime: Arbitrary[LocalDateTime] =
    Arbitrary((LocalDateTime.now()))

  val taxEnrolmentRequest = sample[TaxEnrolmentRequest]

  "The Tax Enrolment Retry repository" when {
    "inserting" should {
      "create a new tax enrolment record" in {
        await(repository.insert(taxEnrolmentRequest).value) shouldBe Right(true)
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        await(repository.insert(taxEnrolmentRequest).value)       shouldBe Right(true)
        await(repository.get(taxEnrolmentRequest.ggCredId).value) shouldBe (Right(Some(taxEnrolmentRequest)))
      }
      "return none if the record does not exist" in {
        await(repository.get("this-gg-cred-id-does-not-exist").value) shouldBe (Right(None))
      }
    }

    "deleting" should {
      "return a count of one when deleting a unique tax enrolment record" in {
        await(repository.insert(taxEnrolmentRequest).value)
        await(repository.delete(taxEnrolmentRequest.ggCredId).value) shouldBe (Right(1))
      }
      "return a count of zero when the tax enrolment record does not exist" in {
        await(repository.delete("this-gg-cred-id-does-not-exist").value) shouldBe (Right(0))
      }
    }
  }
}