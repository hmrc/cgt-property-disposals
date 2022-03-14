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

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class DraftReturnsRepositorySpec extends AnyWordSpec with Matchers with MongoSupport with MockFactory {
  val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.draft-returns.expiry-time = 30days
        | mongodb.draft-returns.max-draft-returns = 10
        |""".stripMargin
    )
  )

  val repository    = new DefaultDraftReturnsRepository(reactiveMongoComponent, config)
  val cgtReference  = sample[CgtReference]
  val cgtReference2 = sample[CgtReference]

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

      "filter out draft returns which cannot be parsed" in {
        implicit val w: OWrites[JsObject] = OWrites.apply(identity)
        val draftReturn                   = sample[DraftReturn]

        val result = repository.collection.insert.one(
          Json
            .parse(
              s"""{
              |  "return" : {
              |    "draftId" : "${UUID.randomUUID().toString}",
              |    "cgtReference" : { "value" : "${cgtReference.value}" },
              |    "draftReturn" : { }
              |  }
              |}""".stripMargin
            )
            .as[JsObject]
        )
        await(result).writeErrors                               shouldBe Seq.empty
        await(repository.save(draftReturn, cgtReference).value) shouldBe Right(())

        await(repository.fetch(cgtReference).value) shouldBe Right(List(draftReturn))
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
