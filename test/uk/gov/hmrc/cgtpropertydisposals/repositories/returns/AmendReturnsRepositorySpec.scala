/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.repositories.returns

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class AmendReturnsRepositorySpec extends AnyWordSpec with Matchers with MongoSupport with MockFactory {
  val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.amend-returns.expiry-time = 24hours
        |""".stripMargin
    )
  )

  val repository = new DefaultAmendReturnsRepository(reactiveMongoComponent, config)

  "DraftAmendReturnsRepository" when {
    "inserting" should {
      "create a new draft return successfully" in {
        val submitReturnRequest = sample[SubmitReturnRequest]
        await(repository.save(submitReturnRequest).value) shouldBe Right(())
      }
    }
    "getting"   should {
      "retrieve an existing record" in {
        val cgtReference         = sample[CgtReference]
        val subscribedDetails    =
          sample[uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails]
            .copy(cgtReference = cgtReference)
        val submitReturnRequest1 = sample[SubmitReturnRequest].copy(subscribedDetails = subscribedDetails)
        val submitReturnRequest2 = sample[SubmitReturnRequest].copy(subscribedDetails = subscribedDetails)

        await(repository.save(submitReturnRequest1).value)       shouldBe Right(())
        await(repository.save(submitReturnRequest2).value)       shouldBe Right(())
        await(repository.fetch(cgtReference).value).map(_.toSet) shouldBe Right(
          Set(submitReturnRequest1, submitReturnRequest2)
        )
      }
    }
  }
}
