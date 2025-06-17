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
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DraftReturnGen.multipleDisposalDraftReturnGen
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.cgtReferenceGen
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DraftReturnsRepositoryFailureSpec extends AnyWordSpec with Matchers with MongoSupport with BeforeAndAfterAll {
  private val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.draft-returns.expiry-time = 30days
        |""".stripMargin
    )
  )

  val repository = new DefaultDraftReturnsRepository(mongoComponent, config)

  private val draftReturn  = sample[DraftReturn]
  private val cgtReference = sample[CgtReference]

  val clientClosed: Future[Unit] =
    repository.collection.countDocuments().toFuture().map(_ => mongoComponent.client.close())

  override protected def beforeAll(): Unit = await(clientClosed)

  "DraftReturnsRepository" when {

    "inserting" should {
      "create a new draft return successfully" in {
        await(repository.save(draftReturn, cgtReference).value).isLeft shouldBe true
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        await(repository.fetch(cgtReference).value).isLeft shouldBe true
      }
    }
  }
}
