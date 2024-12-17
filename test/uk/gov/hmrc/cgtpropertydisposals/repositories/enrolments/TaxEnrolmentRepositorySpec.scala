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

package uk.gov.hmrc.cgtpropertydisposals.repositories.enrolments

import com.typesafe.config.ConfigFactory
import org.scalacheck.Arbitrary
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentRepositorySpec extends AnyWordSpec with Matchers with MongoSupport {
  private val config = Configuration(ConfigFactory.parseString("""
                                                                 |mongodb.tax-enrolment-cache-ttl.expiry-time = 24hours
                                                                 |""".stripMargin))
  val repository     = new DefaultTaxEnrolmentRepository(mongoComponent, config)

  implicit val arbLocalDateTime: Arbitrary[LocalDateTime] =
    Arbitrary((LocalDateTime.now()))

  val taxEnrolmentRequest = sample[TaxEnrolmentRequest]

  "The Tax Enrolment Retry repository" when {
    "inserting" should {
      "create a new tax enrolment record" in {
        await(repository.save(taxEnrolmentRequest).value) shouldBe Right(())
      }
    }

    "getting" should {
      "retrieve an existing record" in {
        await(repository.save(taxEnrolmentRequest).value)         shouldBe Right(())
        await(repository.get(taxEnrolmentRequest.ggCredId).value) shouldBe (Right(Some(taxEnrolmentRequest)))
      }
      "return none if the record does not exist" in {
        await(repository.get("this-gg-cred-id-does-not-exist").value) shouldBe (Right(None))
      }
    }

    "deleting" should {
      "return a count of one when deleting a unique tax enrolment record" in {
        await(repository.save(taxEnrolmentRequest).value)
        await(repository.delete(taxEnrolmentRequest.ggCredId).value) shouldBe (Right(1))
      }
      "return a count of zero when the tax enrolment record does not exist" in {
        await(repository.delete("this-gg-cred-id-does-not-exist").value) shouldBe (Right(0))
      }
    }

    "updating" should {
      val updatedTaxEnrolmentRequest = sample[TaxEnrolmentRequest]

      "return a the updated tax enrolment record" in {
        val result = await(repository.save(taxEnrolmentRequest).value)
        result                                                                                   shouldBe result
        await(repository.update(taxEnrolmentRequest.ggCredId, updatedTaxEnrolmentRequest).value) shouldBe Right(
          Some(updatedTaxEnrolmentRequest)
        )
      }
    }

  }
}
