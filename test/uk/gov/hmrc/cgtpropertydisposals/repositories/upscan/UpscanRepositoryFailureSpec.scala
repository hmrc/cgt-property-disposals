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

package uk.gov.hmrc.cgtpropertydisposals.repositories.upscan

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UpscanReference, UpscanUpload}
import uk.gov.hmrc.cgtpropertydisposals.repositories.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanRepositoryFailureSpec extends WordSpec with Matchers with MongoSupport {
  val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.upscan.expiry-time = 7days
        |""".stripMargin
    )
  )

  val repository = new DefaultUpscanRepository(reactiveMongoComponent, config)
  reactiveMongoComponent.mongoConnector.helper.driver.close()

  "Upscan Repository" when {
    "inserting" should {
      "insert a new upscan upload document" in {
        val upscanUpload = sample[UpscanUpload]
        await(repository.insert(upscanUpload).value).isLeft shouldBe true
      }
    }

    "updating an upscan upload document" should {
      "update an existing upscan upload document" in {

        val upscanUpload    = sample[UpscanUpload]
        val draftReturnId   = upscanUpload.draftReturnId
        val upscanReference = UpscanReference(upscanUpload.upscanUploadMeta.reference)

        await(
          repository
            .select(draftReturnId, upscanReference)
            .value
        ).isLeft shouldBe true
      }
    }

    "selecting an upscan upload document" should {
      "select an upscan upload document if it exists" in {
        val upscanUpload    = sample[UpscanUpload]
        val draftReturnId   = upscanUpload.draftReturnId
        val upscanReference = UpscanReference(upscanUpload.upscanUploadMeta.reference)
        await(
          repository
            .select(draftReturnId, upscanReference)
            .value
        ).isLeft shouldBe true
      }
    }
  }

}
