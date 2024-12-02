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

package uk.gov.hmrc.cgtpropertydisposals.service.dms

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.ByteString
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cgtpropertydisposals.connectors.dms.DmsConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.dms._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.CompleteMultipleDisposalsReturn
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MandatoryEvidence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.CompleteSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.service.upscan.UpscanService
import uk.gov.hmrc.cgtpropertydisposals.util.FileIOExecutionContext
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class DmsSubmissionServiceSpec extends AnyWordSpec with Matchers with IdiomaticMockito {
  private val mockDmsConnector  = mock[DmsConnector]
  private val mockUpscanService = mock[UpscanService]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val config = Configuration(
    ConfigFactory.parseString(
      """
        | dms {
        |   queue-name = "queue-name"
        |   b64-business-area = "YnVzaW5lc3MtYXJlYQ=="
        | }
        | 
        |""".stripMargin
    )
  )

  private def mockDmsSubmission(dmsSubmissionPayload: DmsSubmissionPayload)(
    response: DmsEnvelopeId
  ) =
    mockDmsConnector
      .submitToDms(dmsSubmissionPayload)
      .returns(Future.successful(response))

  private def mockDownloadS3Urls(upscanSuccesses: List[UpscanSuccess])(
    response: List[Either[Error, FileAttachment]]
  ) =
    mockUpscanService
      .downloadFilesFromS3(upscanSuccesses)
      .returns(Future.successful(response))

  private val actorSystem                                                  = ActorSystem()
  implicit val dmsSubmissionPollerExecutionContext: FileIOExecutionContext =
    new FileIOExecutionContext(actorSystem)

  val dmsSubmissionService =
    new DefaultDmsSubmissionService(mockDmsConnector, mockUpscanService, config)

  "Dms Submission Service" when {
    "a dms file submission request is made" must {
      val cgtReference   = sample[CgtReference]
      val dmsMetadata    =
        DmsMetadata("form-bundle-id", cgtReference.value, "queue-name", "business-area")
      val upscanSuccess  = UpscanSuccess(
        "reference",
        "status",
        "downloadUrl",
        Map.empty
      )
      val completeReturn = sample[CompleteMultipleDisposalsReturn].copy(
        yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers].copy(
          mandatoryEvidence = Some(sample[MandatoryEvidence].copy(upscanSuccess = upscanSuccess))
        ),
        supportingDocumentAnswers =
          CompleteSupportingEvidenceAnswers(doYouWantToUploadSupportingEvidence = false, List.empty)
      )

      "return an error" when {
        "some of the downloads have failed" in {
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), Seq(ByteString(1))))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          mockDownloadS3Urls(List(upscanSuccess))(
            List(Left(Error("error downloading file")))
          )

          await(
            dmsSubmissionService
              .submitToDms(
                dmsSubmissionPayload.b64Html,
                "form-bundle-id",
                cgtReference,
                completeReturn
              )
              .value
          ).isLeft shouldBe true
        }
      }

      "return an envelope id when files have been successfully submitted to the dms service" in {
        val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), Seq(ByteString(1))))
        val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

        mockDownloadS3Urls(List(upscanSuccess))(fileAttachments.map(Right(_)))
        mockDmsSubmission(dmsSubmissionPayload)(DmsEnvelopeId("env-id"))
        await(
          dmsSubmissionService
            .submitToDms(
              dmsSubmissionPayload.b64Html,
              "form-bundle-id",
              cgtReference,
              completeReturn
            )
            .value
        ) shouldBe Right(
          DmsEnvelopeId(
            "env-id"
          )
        )
      }
    }
  }
}
