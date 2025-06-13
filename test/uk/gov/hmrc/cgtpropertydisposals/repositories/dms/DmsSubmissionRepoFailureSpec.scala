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

package uk.gov.hmrc.cgtpropertydisposals.repositories.dms

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DmsSubmissionGen.given
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionRequest
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Failed, PermanentlyFailed, Succeeded}
import uk.gov.hmrc.mongo.workitem.WorkItem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DmsSubmissionRepoFailureSpec
    extends AnyWordSpec
    with Matchers
    with MongoSupport
    with BeforeAndAfterAll
    with Eventually {

  private val config = Configuration(
    ConfigFactory.parseString(
      """
        |dms {
        |    queue-name = "queue-name"
        |    b64-business-area = "YnVzaW5lc3MtYXJlYQ=="
        |    submission-poller {
        |        initial-delay = 10 seconds
        |        interval = 1 seconds
        |        failure-count-limit = 10
        |        in-progress-retry-after = 1000
        |        mongo {
        |            ttl = 604800 seconds # 7 days
        |        }
        |    }
        |}
        |""".stripMargin
    )
  )

  val repository = new DefaultDmsSubmissionRepo(
    mongoComponent,
    config
  )

  val clientClosed: Future[Unit] = repository.count(Succeeded).map(_ => mongoComponent.client.close())

  override protected def beforeAll(): Unit = await(clientClosed)

  "A broken DmsSubmission Repo" when {
    "inserting" should {
      "return an error" in {
        val dmsSubmissionRequest = sample[DmsSubmissionRequest]
        await(repository.set(dmsSubmissionRequest).value).isLeft shouldBe true
      }
    }

    "getting" should {
      "return an error" in {
        await(repository.get.value).isLeft shouldBe true
      }
    }

    "set processing status" should {
      "return an error" in {
        val workItem = sample[WorkItem[DmsSubmissionRequest]]
        await(repository.setProcessingStatus(workItem.id, Failed).value).isLeft shouldBe true
      }
    }

    "set result status" should {
      "update the work item status" in {
        val workItem = sample[WorkItem[DmsSubmissionRequest]]
        await(repository.setResultStatus(workItem.id, PermanentlyFailed).value).isLeft shouldBe true
      }
    }
  }
}
