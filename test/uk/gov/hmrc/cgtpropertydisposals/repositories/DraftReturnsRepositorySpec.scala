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

package uk.gov.hmrc.cgtpropertydisposals.repositories

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DefaultDraftReturnsRepository
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._

import scala.concurrent.ExecutionContext.Implicits.global

class DraftReturnsRepositorySpec extends WordSpec with Matchers with MongoSupport with MockFactory {

  private val cacheRepository = mock[CacheRepository]

  val repository  = new DefaultDraftReturnsRepository(reactiveMongoComponent, cacheRepository)
  val draftReturn = sample[DraftReturn]

  "DraftReturnsRepository" when {
    "inserting" should {
      "create a new draft return successfully" in {
        repository.save(draftReturn).value shouldBe Right(())
      }
    }
  }
}
