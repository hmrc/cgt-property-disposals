/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.OnboardingGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.SubmitReturnGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnWrapper}
import uk.gov.hmrc.cgtpropertydisposals.repositories.CurrentInstant
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class AmendReturnsRepositorySpec extends AnyWordSpec with Matchers with CleanMongoCollectionSupport {
  private val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.amend-returns.expiry-time = 24hours
        |""".stripMargin
    )
  )

  private val stagedInstant = sample[SubmitReturnWrapper].lastUpdated

  private val currentInstant = new CurrentInstant {
    override def currentInstant(): Instant = stagedInstant
  }

  val repository = new DefaultAmendReturnsRepository(mongoComponent, config, currentInstant)

  "DraftAmendReturnsRepository" when {
    "inserting" should {
      "create a new draft return successfully" in {
        val submitReturnRequest = sample[SubmitReturnRequest]
        await(repository.save(submitReturnRequest).value) shouldBe Right(())
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        val cgtReference         = sample[CgtReference]
        val subscribedDetails    =
          sample[uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails]
            .copy(cgtReference = cgtReference)
        val submitReturnRequest1 = sample[SubmitReturnRequest].copy(subscribedDetails = subscribedDetails)
        val submitReturnRequest2 = sample[SubmitReturnRequest].copy(subscribedDetails = subscribedDetails)

        val submitReturnWrapper1 = sample[SubmitReturnWrapper]
          .copy(
            id = submitReturnRequest1.id.toString,
            submitReturnRequest = submitReturnRequest1,
            lastUpdated = stagedInstant
          )
        val submitReturnWrapper2 = sample[SubmitReturnWrapper]
          .copy(
            id = submitReturnRequest2.id.toString,
            submitReturnRequest = submitReturnRequest2,
            lastUpdated = stagedInstant
          )

        await(repository.save(submitReturnRequest1).value)       shouldBe Right(())
        await(repository.save(submitReturnRequest2).value)       shouldBe Right(())
        await(repository.fetch(cgtReference).value).map(_.toSet) shouldBe Right(
          Set(submitReturnWrapper1, submitReturnWrapper2)
        )
      }
    }
  }
}
