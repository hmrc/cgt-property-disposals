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
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DraftReturnGen.singleMixedUseDraftReturnGen
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.cgtReferenceGen
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.ExecutionContext.Implicits.global

class DraftReturnsRepositorySpec extends AnyWordSpec with Matchers with CleanMongoCollectionSupport {

  override def beforeEach(): Unit =
    dropDatabase()

  private val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.draft-returns.expiry-time = 30days
        |""".stripMargin
    )
  )

  val repository            = new DefaultDraftReturnsRepository(mongoComponent, config)
  private val cgtReference  = sample[CgtReference]
  private val cgtReference2 = sample[CgtReference]

  "DraftReturnsRepository" when {
    "inserting" should {
      "create a new draft return successfully" in {
        val draftReturn = sample[DraftReturn]
        await(repository.save(draftReturn, cgtReference).value) shouldBe Right(())
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        val draftReturn  = sample[DraftReturn]
        val draftReturn2 = sample[DraftReturn]

        await(repository.save(draftReturn, cgtReference).value)  shouldBe Right(())
        await(repository.save(draftReturn2, cgtReference).value) shouldBe Right(())
        await(repository.fetch(cgtReference).value).map(_.toSet) shouldBe Right(
          Set(draftReturn, draftReturn2)
        )
      }
    }

    "deleting" should {
      "delete single return with the given cgtReference" in {
        val draftReturn  = sample[DraftReturn]
        val draftReturn2 = sample[DraftReturn]

        await(repository.save(draftReturn, cgtReference).value)   shouldBe Right(())
        await(repository.save(draftReturn2, cgtReference2).value) shouldBe Right(())
        await(repository.fetch(cgtReference).value).map(_.toSet)  shouldBe Right(
          Set(draftReturn)
        )
        await(repository.fetch(cgtReference2).value).map(_.toSet) shouldBe Right(
          Set(draftReturn2)
        )

        await(repository.delete(cgtReference).value)              shouldBe Right(())
        await(repository.fetch(cgtReference).value)               shouldBe Right(List.empty)
        await(repository.fetch(cgtReference2).value).map(_.toSet) shouldBe Right(
          Set(draftReturn2)
        )
      }

      "delete multiple returns with the same given cgtReference" in {
        val draftReturn  = sample[DraftReturn]
        val draftReturn2 = sample[DraftReturn]

        await(repository.save(draftReturn, cgtReference).value)  shouldBe Right(())
        await(repository.save(draftReturn2, cgtReference).value) shouldBe Right(())
        await(repository.fetch(cgtReference).value).map(_.toSet) shouldBe Right(
          Set(draftReturn, draftReturn2)
        )

        await(repository.delete(cgtReference).value) shouldBe Right(())
        await(repository.fetch(cgtReference).value)  shouldBe Right(List.empty)
      }

      "delete all draft returns with the given ids" in {
        val draftReturn  = sample[DraftReturn]
        val draftReturn2 = sample[DraftReturn]

        await(repository.save(draftReturn, cgtReference).value)                  shouldBe Right(())
        await(repository.save(draftReturn2, cgtReference).value)                 shouldBe Right(())
        await(repository.fetch(cgtReference).value).map(_.toSet)                 shouldBe Right(
          Set(draftReturn, draftReturn2)
        )
        await(repository.deleteAll(List(draftReturn.id, draftReturn2.id)).value) shouldBe Right(())
        await(repository.fetch(cgtReference).value)                              shouldBe Right(List.empty)
      }
    }
  }
}
