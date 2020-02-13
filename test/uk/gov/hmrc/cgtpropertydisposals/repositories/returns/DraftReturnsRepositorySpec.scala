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

package uk.gov.hmrc.cgtpropertydisposals.repositories.returns

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

class DraftReturnsRepositorySpec extends WordSpec with Matchers with MongoSupport with MockFactory {
  val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.draft-returns.expiry-time = 30days
        | mongodb.draft-returns.max-draft-returns = 10
        |""".stripMargin
    )
  )

  val repository  = new DefaultDraftReturnsRepository(reactiveMongoComponent, config)
  val draftReturn = sample[DraftReturn]

  "DraftReturnsRepository" when {
    "inserting" should {
      "create a new draft return successfully" in {
        await(repository.save(draftReturn).value) shouldBe Right(())
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        val draftReturn2 = sample[DraftReturn].copy(cgtReference = draftReturn.cgtReference)

        await(repository.save(draftReturn).value)  shouldBe Right(())
        await(repository.save(draftReturn2).value) shouldBe Right(())

        await(repository.fetch(draftReturn.cgtReference).value).map(_.toSet) shouldBe Right(
          Set(draftReturn, draftReturn2)
        )
      }
    }

  }
}