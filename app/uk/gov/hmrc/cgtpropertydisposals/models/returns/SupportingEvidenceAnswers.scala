/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json.{JsError, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UploadReference, UpscanUploadMeta}

import java.time.LocalDateTime

sealed trait SupportingEvidenceAnswers extends Product with Serializable

object SupportingEvidenceAnswers {

  final case class SupportingEvidence(
    uploadReference: UploadReference,
    upscanUploadMeta: UpscanUploadMeta,
    uploadedOn: LocalDateTime,
    upscanSuccess: UpscanSuccess,
    fileName: String
  )

  object SupportingEvidence {
    implicit val format: OFormat[SupportingEvidence] = Json.format[SupportingEvidence]
  }

  final case class IncompleteSupportingEvidenceAnswers(
    doYouWantToUploadSupportingEvidence: Option[Boolean],
    evidences: List[SupportingEvidence],
    expiredEvidences: List[SupportingEvidence]
  ) extends SupportingEvidenceAnswers

  object IncompleteSupportingEvidenceAnswers {
    val empty: IncompleteSupportingEvidenceAnswers =
      IncompleteSupportingEvidenceAnswers(None, List.empty, List.empty)
  }

  final case class CompleteSupportingEvidenceAnswers(
    doYouWantToUploadSupportingEvidence: Boolean,
    evidences: List[SupportingEvidence]
  ) extends SupportingEvidenceAnswers

  implicit class UploadSupportingDocumentsOps(private val a: SupportingEvidenceAnswers) extends AnyVal {

    def fold[A](
      ifIncomplete: IncompleteSupportingEvidenceAnswers => A,
      ifComplete: CompleteSupportingEvidenceAnswers => A
    ): A =
      a match {
        case i: IncompleteSupportingEvidenceAnswers => ifIncomplete(i)
        case c: CompleteSupportingEvidenceAnswers   => ifComplete(c)
      }

  }

  implicit val completeFormat: OFormat[CompleteSupportingEvidenceAnswers]     =
    Json.format[CompleteSupportingEvidenceAnswers]
  implicit val inCompleteFormat: OFormat[IncompleteSupportingEvidenceAnswers] =
    Json.format[IncompleteSupportingEvidenceAnswers]

  implicit val format: OFormat[SupportingEvidenceAnswers] = new OFormat[SupportingEvidenceAnswers] {
    override def reads(json: JsValue): JsResult[SupportingEvidenceAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteSupportingEvidenceAnswers", value) =>
            value.validate[IncompleteSupportingEvidenceAnswers]
          case ("CompleteSupportingEvidenceAnswers", value)   =>
            value.validate[CompleteSupportingEvidenceAnswers]
          case (other, _)                                     =>
            JsError(s"Unrecognized SupportingEvidenceAnswers type: $other")
        }
      case _                                    =>
        JsError("Expected SupportingEvidenceAnswers wrapper object with a single entry")
    }

    override def writes(a: SupportingEvidenceAnswers): JsObject = a match {
      case i: IncompleteSupportingEvidenceAnswers =>
        Json.obj("IncompleteSupportingEvidenceAnswers" -> Json.toJson(i))
      case c: CompleteSupportingEvidenceAnswers   =>
        Json.obj("CompleteSupportingEvidenceAnswers" -> Json.toJson(c))
    }
  }

}
