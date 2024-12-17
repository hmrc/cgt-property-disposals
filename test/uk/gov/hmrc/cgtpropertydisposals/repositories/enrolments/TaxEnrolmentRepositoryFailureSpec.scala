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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxEnrolmentRepositoryFailureSpec extends AnyWordSpec with Matchers with MongoSupport with BeforeAndAfterAll {
  private val config = Configuration(ConfigFactory.parseString("""
                                                                 |mongodb.tax-enrolment-cache-ttl.expiry-time = 24hours
                                                                 |""".stripMargin))
  val repository     = new DefaultTaxEnrolmentRepository(mongoComponent, config)

  implicit val arbLocalDateTime: Arbitrary[LocalDateTime] =
    Arbitrary((LocalDateTime.now()))

  val taxEnrolmentRequest: TaxEnrolmentRequest = sample[TaxEnrolmentRequest]

  val clientClosed: Future[Unit] =
    repository.collection.countDocuments().toFuture().map(_ => mongoComponent.client.close())

  override protected def beforeAll(): Unit = await(clientClosed)

  "The Tax Enrolment Retry repository" when {

    "inserting into a broken repository" should {
      "fail the insert" in {
        await(repository.save(taxEnrolmentRequest).value).isLeft shouldBe true
      }
    }

    "getting from a broken repository" should {
      "fail the get" in {
        await(repository.get(taxEnrolmentRequest.ggCredId).value).isLeft shouldBe true
      }
    }

    "deleting from a broken repository" should {
      "fail the delete" in {
        await(repository.delete(taxEnrolmentRequest.ggCredId).value).isLeft shouldBe true
      }
    }
    "updating on a broken repository"   should {
      val updatedTaxEnrolmentRequest = sample[TaxEnrolmentRequest]
      "fail to update" in {
        await(repository.save(taxEnrolmentRequest).value)
        await(repository.update(taxEnrolmentRequest.ggCredId, updatedTaxEnrolmentRequest).value).isLeft shouldBe true
      }
    }
  }
}
