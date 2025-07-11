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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest

import java.time.{Clock, Instant}

sealed trait DesReturnType extends Product with Serializable {
  val source: String
  val submissionType: SubmissionType
}

final case class CreateReturnType(
  source: String
) extends DesReturnType {
  val submissionType: SubmissionType = SubmissionType.New
}

final case class AmendReturnType(
  source: String,
  submissionID: Option[String]
) extends DesReturnType {
  val submissionType: SubmissionType = SubmissionType.Amend
}

object DesReturnType {

  def apply(submitReturnRequest: SubmitReturnRequest, clock: Clock = Clock.systemUTC()): DesReturnType = {
    val timestamp    =
      if submitReturnRequest.isFurtherReturn then s" ${Instant.now(clock).getEpochSecond.toString}"
      else ""
    val returnSource =
      submitReturnRequest.agentReferenceNumber
        .fold(s"${"self digital" + timestamp}")(_ => s"${"agent digital" + timestamp}")

    submitReturnRequest.amendReturnData
      .fold[DesReturnType](CreateReturnType(returnSource))(amendReturnData =>
        AmendReturnType(returnSource, Some(amendReturnData.originalReturn.summary.submissionId))
      )
  }

  implicit val returnTypeFormat: OFormat[DesReturnType] = new OFormat[DesReturnType] {
    override def writes(o: DesReturnType): JsObject = o match {
      case c: CreateReturnType =>
        JsObject(Map("source" -> JsString(c.source), "submissionType" -> Json.toJson(c.submissionType)))
      case a: AmendReturnType  =>
        JsObject(
          Map(
            "source"         -> JsString(a.source),
            "submissionType" -> Json.toJson(a.submissionType),
            "submissionID"   -> a.submissionID.fold[JsValue](JsNull)(JsString.apply)
          )
        )
    }

    override def reads(json: JsValue): JsResult[DesReturnType] =
      for
        source         <- (json \ "source").validate[String]
        submissionType <- (json \ "submissionType").validate[SubmissionType]
        result         <- submissionType match {
                            case SubmissionType.New   => JsSuccess(CreateReturnType(source))
                            case SubmissionType.Amend => JsSuccess(AmendReturnType(source, None))
                          }
      yield result
  }
}
