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
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class AmendReturnsRepositoryFailureSpec extends WordSpec with Matchers with MongoSupport with MockFactory {
  val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.amend-returns.expiry-time = 24hours
        |""".stripMargin
    )
  )

  val repository = new DefaultAmendReturnsRepository(reactiveMongoComponent, config)

  val submitReturnRequest = sample[SubmitReturnRequest]
  val cgtReference        = sample[CgtReference]

  "AmendReturnsRepository" when {
    reactiveMongoComponent.mongoConnector.helper.driver.close()

    "inserting" should {
      "insert an amend return request successfully" in {
        await(repository.save(submitReturnRequest, cgtReference).value).isLeft shouldBe true
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        await(repository.fetch(cgtReference).value).isLeft shouldBe true
      }
    }
  }

}
