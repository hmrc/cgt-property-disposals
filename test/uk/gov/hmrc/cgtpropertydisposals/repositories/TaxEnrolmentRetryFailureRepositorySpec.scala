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

class TaxEnrolmentRetryFailureRepositorySpec extends WordSpec with Matchers with MongoSupport with TestSupport {

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
    "inserting into a broken repository" should {
      reactiveMongoComponent.mongoConnector.helper.driver.close()
      "fail the insert" in {
        await(repository.insert(inProgressEnrolment).value).isLeft shouldBe true
      }
    }

    "recovering all retry records" should {
      "fail" in {
        await(repository.getAllNonFailedEnrolmentRequests().value).isLeft shouldBe true
      }
    }

    "failing an enrolment retry record" should {
      "fail" in {
        await(repository.updateStatusToFail("userId-1").value).isLeft shouldBe true
      }
    }

    "deleting" should {
      "fail " in {
        await(repository.delete("userId-1").value).isLeft shouldBe true
      }
    }
  }
}
