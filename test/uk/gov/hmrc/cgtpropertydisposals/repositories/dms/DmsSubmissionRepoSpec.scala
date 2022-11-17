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

package uk.gov.hmrc.cgtpropertydisposals.repositories.dms

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionRequest
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Failed, InProgress, PermanentlyFailed}
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class DmsSubmissionRepoSpec extends AnyWordSpec with Matchers with MongoSupport with MockFactory {
  val config = Configuration(
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
    config,
    new ServicesConfig(config)
  )

  "DmsSubmission Repo" when {
    "set" should {
      "insert a dms request" in {
        val dmsSubmissionRequest = sample[DmsSubmissionRequest]
        await(repository.set(dmsSubmissionRequest).value).map(item => item.item) shouldBe Right(dmsSubmissionRequest)
      }
    }

    "get" should {
      "return some work item if one exists" in {

        val dmsSubmissionRequest = sample[DmsSubmissionRequest]
        await(repository.set(dmsSubmissionRequest).value).map(item => item.item) shouldBe Right(dmsSubmissionRequest)

        await(repository.get.value).map(maybeWorkItem => maybeWorkItem.map(workItem => workItem.item)) shouldBe Right(
          Some(dmsSubmissionRequest)
        )

      }
      "return none if no work item exists " in {
        await(
          repository.get.value
        ).map(mw => mw.map(s => s.item)) shouldBe Right(None)
      }
    }

    "set processing status" should {

      "update the work item status" in {
        val dmsSubmissionRequest                                           = sample[DmsSubmissionRequest]
        val workItem: Either[models.Error, WorkItem[DmsSubmissionRequest]] =
          await(repository.set(dmsSubmissionRequest).value)
        workItem.map(wi => await(repository.setProcessingStatus(wi.id, Failed).value) shouldBe Right(true))
      }

    }

    "set result status" should {
      "update the work item status" in {
        val dmsSubmissionRequest                                           = sample[DmsSubmissionRequest]
        val workItem: Either[models.Error, WorkItem[DmsSubmissionRequest]] =
          await(repository.set(dmsSubmissionRequest).value)
        val _                                                              =
          workItem.map(wi => await(repository.setProcessingStatus(wi.id, InProgress).value))
        workItem.map(wi => await(repository.setResultStatus(wi.id, PermanentlyFailed).value) shouldBe Right(true))
      }
    }
  }

}
