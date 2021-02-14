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
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest

import scala.concurrent.ExecutionContext.Implicits.global

class VerifiersRepositoryFailureSpec extends WordSpec with Matchers with MongoSupport {

  val repository = new DefaultVerifiersRepository(reactiveMongoComponent)

  implicit val arbLocalDateTime: Arbitrary[LocalDateTime] =
    Arbitrary((LocalDateTime.now()))

  val updateVerifierDetails = sample[UpdateVerifiersRequest]

  "The Update Verifiers repository" when {

    repository.count.map(_ => reactiveMongoComponent.mongoConnector.helper.driver.close())

    "inserting into a broken repository" should {
      "fail the insert" in {
        await(repository.insert(updateVerifierDetails).value).isLeft shouldBe true
      }
    }

    "getting from a broken repository" should {
      "fail the get" in {
        await(repository.get(updateVerifierDetails.ggCredId).value).isLeft shouldBe true
      }
    }

    "deleting from a broken repository" should {
      "fail the delete" in {
        await(repository.delete(updateVerifierDetails.ggCredId).value).isLeft shouldBe true
      }
    }
  }
}
