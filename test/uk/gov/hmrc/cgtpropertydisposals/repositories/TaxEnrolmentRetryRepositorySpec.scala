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

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.TestSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest.TaxEnrolmentFailed

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentRetryRepositorySpec extends WordSpec with Matchers with MongoSupport with TestSupport {

  val repository = new DefaultTaxEnrolmentRetryRepository(reactiveMongoComponent)
  val inProgressEnrolment =
    TaxEnrolmentRequest("userId-1", 1, "test-cgt-reference", UkAddress("line1", None, None, None, "BN11 3JB"))

  val failedEnrolment =
    TaxEnrolmentRequest(
      "userId-2",
      1,
      "test-cgt-reference",
      UkAddress("line1", None, None, None, "BN11 3JB"),
      status = TaxEnrolmentFailed
    )

  "The Tax Enrolment Retry repository" when {
    "inserting" should {
      "create a new tax enrolment retry record" in {
        await(repository.insert(inProgressEnrolment).value) shouldBe Right(true)
      }
    }

//    "checking if a record exists" should {
//      "return false if the record does not exist" in {
//        await(repository.exists(failedEnrolment.userId).value) shouldBe (Right(None))
//      }
//
//      "return true if the record does exist" in {
//
//        val tr = TaxEnrolmentRequest(
//          "userId-1",
//          "test-cgt-reference",
//          UkAddress("line1", None, None, None, "BN11 3JB"),
//          "Failed"
//        )
//
//        await(
//          repository
//            .insert(
//              tr
//            )
//            .value
//        )
//
//        await(
//          repository
//            .exists(
//              tr.userId
//            )
//            .value
//        ) shouldBe (Right(Some(tr)))
//      }
//
//    }

    "recovering all tax enrolment records" should {
      "return a list of one record when there is one outstanding tax enrolment request" in {
        await(repository.insert(inProgressEnrolment).value)
        await(repository.getAllNonFailedEnrolmentRequests().value) shouldBe (Right(List(inProgressEnrolment)))
      }

      "return empty list when there are no fail records" in {
        await(
          repository
            .getAllNonFailedEnrolmentRequests()
            .value
        ) shouldBe (Right(List.empty))
      }
    }

    "updating the status of a failed tax enrolment record" should {
      "return the updated tax enrolment record" in {
        await(repository.insert(inProgressEnrolment).value)
        await(repository.updateStatusToFail("userId-1").value) shouldBe (Right(
          Some(inProgressEnrolment.copy(status = TaxEnrolmentFailed))
        ))
      }
    }

    "deleting a tax enrolment record" should {
      "return a count of one when deleting a unique tax enrolment record" in {
        await(repository.insert(inProgressEnrolment).value)
        await(repository.delete(inProgressEnrolment.userId).value) shouldBe (Right(1))
      }

      "return a count of zero when the tax enrolment record does not exist" in {
        await(repository.delete("this-user-id-does-not-exist").value) shouldBe (Right(0))
      }
    }
  }
}
