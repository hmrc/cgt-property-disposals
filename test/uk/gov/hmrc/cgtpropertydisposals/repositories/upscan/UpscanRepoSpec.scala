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

package uk.gov.hmrc.cgtpropertydisposals.repositories.upscan

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.generators.UpscanGen.upscanUploadGen
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanUpload
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.{Clock, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class UpscanRepoSpec extends AnyWordSpec with MongoSupport with ScalaFutures with DefaultAwaitTimeout with Matchers {

  private val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.upscan.expiry-time = 20seconds
        |""".stripMargin
    )
  )

  protected def beforeAll(): Unit =
    dropDatabase()

  protected val repository = new DefaultUpscanRepo(mongoComponent, config)

  "Upscan Repository" when {
    "inserting" should {
      "insert a new upscan upload document" in {
        val upscanUpload = sample[UpscanUpload].copy(uploadedOn = LocalDateTime.now(Clock.systemUTC()))
        await(repository.insert(upscanUpload).value) shouldBe Right(())
      }
    }
  }
}
