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

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.TestSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentRetryRepositorySpec extends WordSpec with Matchers with MongoSupport with TestSupport {

  val repository = new DefaultTaxEnrolmentRetryRepository(reactiveMongoComponent)

  "The Tax Enrolment Retry repository" when {
    "inserting" should {
      "create a new tax enrolment retry record" in {
        await(
          repository
            .insert(
              TaxEnrolmentRequest(
                "userId-1",
                "test-cgt-reference",
                UkAddress("line1", None, None, None, "BN11 3JB"),
                "InProgress"
              )
            )
            .value
        ) shouldBe Right(true)
      }
    }

    "recovering all retry records" should {
      "return empty list when there are no fail records" in {
        await(
          repository
            .insert(
              TaxEnrolmentRequest(
                "userId-1",
                "test-cgt-reference",
                UkAddress("line1", None, None, None, "BN11 3JB"),
                "InProgress"
              )
            )
            .value
        )

        await(
          repository
            .getAllNonFailedEnrolmentRequests()
            .value
        ) shouldBe (Right(List.empty))
      }
    }

    "failing an enrolment retry record" should {
      "update a record from in-progress state to failed state" in {
        await(
          repository
            .insert(
              TaxEnrolmentRequest(
                "userId-1",
                "test-cgt-reference",
                UkAddress("line1", None, None, None, "BN11 3JB"),
                "InProgress",
                0,
                0,
                0,
                LocalDateTime.of(2019, 9, 18, 12, 15)
              )
            )
            .value
        )

        await(
          repository
            .updateStatusToFail("userId-1")
            .value
        ) shouldBe (Right(
          Some(
            TaxEnrolmentRequest(
              "userId-1",
              "test-cgt-reference",
              UkAddress("line1", None, None, None, "BN11 3JB"),
              "Failed",
              0,
              0,
              0,
              LocalDateTime.of(2019, 9, 18, 12, 15)
            )
          )
        ))
      }
    }

    "deleting" should {
      "delete an existing a record" in {
        await(
          repository
            .insert(
              TaxEnrolmentRequest(
                "userId-1",
                "test-cgt-reference",
                UkAddress("line1", None, None, None, "BN11 3JB"),
                "InProgress"
              )
            )
            .value
        )

        await(
          repository
            .delete("userId-1")
            .value
        ) shouldBe (Right(1))
      }

      "delete a non-existent record" in {
        await(
          repository
            .delete("this-delete-id-does-not-exist")
            .value
        ) shouldBe (Right(0))
      }
    }
  }
}
