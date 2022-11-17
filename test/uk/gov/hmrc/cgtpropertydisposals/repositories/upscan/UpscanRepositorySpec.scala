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

package uk.gov.hmrc.cgtpropertydisposals.repositories.upscan

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UploadReference, UpscanUpload, UpscanUploadWrapper}
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.{Clock, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class UpscanRepositorySpec extends AnyWordSpec with Matchers with MongoSupport {

  val config = Configuration(
    ConfigFactory.parseString(
      """
        | mongodb.upscan.expiry-time = 20seconds
        |""".stripMargin
    )
  )

  val repository = new DefaultUpscanRepository(mongoComponent, config)

  "Upscan Repository" when {
    "inserting" should {
      "insert a new upscan upload document" in {
        val upscanUpload = sample[UpscanUpload].copy(uploadedOn = LocalDateTime.now(Clock.systemUTC()))
        await(repository.insert(upscanUpload).value) shouldBe Right(())
      }
    }

    "updating an upscan upload document" should {
      "update an existing upscan upload document" in {

        val upscanUpload = sample[UpscanUpload].copy(uploadedOn = LocalDateTime.now(Clock.systemUTC()))

        await(repository.insert(upscanUpload).value) shouldBe Right(())

        val newUpscanUpload = sample[UpscanUpload].copy(
          uploadReference = UploadReference(s"${upscanUpload.uploadReference}-2"),
          uploadedOn = LocalDateTime.now(Clock.systemUTC())
        )
//        val upscanUploadNew = sample[UpscanUploadWrapper].copy(
//          s"${upscanUpload.uploadReference.value}-2",
//          newUpscanUpload
//        )

        await(
          repository
            .update(
              upscanUpload.uploadReference,
              newUpscanUpload
            )
            .value
        ) shouldBe Right(())

        await(
          repository
            .select(upscanUpload.uploadReference)
            .value
        ) shouldBe Right(Some(newUpscanUpload))
      }
    }

    "selecting upscan upload documents" should {
      "select an upscan upload document if it exists" in {
        import java.time.Instant

        val k  = LocalDateTime.now(Clock.systemUTC())
        val in = Instant.now()

        val upscanUpload  = sample[UpscanUpload].copy(uploadedOn = k)
        val upscanUpload2 = sample[UpscanUpload].copy(uploadedOn = k)

        val upscanUploadNew = sample[UpscanUploadWrapper].copy(
          id = upscanUpload.uploadReference.value,
          upscan = upscanUpload,
          lastUpdated = in
        )

        await(repository.insert(upscanUpload).value)  shouldBe Right(())
        await(repository.insert(upscanUpload2).value) shouldBe Right(())

        await(
          repository
            .select(upscanUpload.uploadReference)
            .value
        ) shouldBe Right(Some(upscanUploadNew))

        await(repository.selectAll(List(upscanUpload.uploadReference, upscanUpload2.uploadReference)).value)
          .map(_.toSet) shouldBe Right(
          Set(upscanUpload, upscanUpload2)
        )

      }
    }
  }
}
