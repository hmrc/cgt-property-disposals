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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import java.time.{Clock, Instant}

import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest

sealed trait ReturnType extends Product with Serializable {
  val source: String
  val submissionType: SubmissionType
}

final case class CreateReturnType(
  source: String
) extends ReturnType {
  val submissionType: SubmissionType = SubmissionType.New
}

final case class AmendReturnType(
  source: String,
  submissionID: String
) extends ReturnType {
  val submissionType: SubmissionType = SubmissionType.Amend
}

object ReturnType {

  def apply(submitReturnRequest: SubmitReturnRequest, clock: Clock = Clock.systemUTC()): ReturnType = {
    val timestamp    =
      if (submitReturnRequest.isFurtherReturn) s" ${Instant.now(clock).getEpochSecond.toString}"
      else ""
    val returnSource =
      submitReturnRequest.agentReferenceNumber
        .fold(s"${"self digital" + timestamp}")(_ => s"${"agent digital" + timestamp}")

    submitReturnRequest.originalReturnFormBundleId
      .fold[ReturnType](CreateReturnType(returnSource))(AmendReturnType(returnSource, _))
  }

  implicit val returnTypeFormat: OFormat[ReturnType] =
    OFormat[ReturnType](
      json =>
        for {
          source         <- (json \ "source").validate[String]
          submissionType <- (json \ "submissionType").validate[SubmissionType]
          submissionId   <- (json \ "submissionID").validateOpt[String]
          result         <- submissionType match {
                              case SubmissionType.New   => JsSuccess(CreateReturnType(source))
                              case SubmissionType.Amend =>
                                submissionId.fold[JsResult[AmendReturnType]](
                                  JsError("Could not find submission id for amend return type")
                                )(id => JsSuccess(AmendReturnType(source, id)))
                            }
        } yield result,
      { r: ReturnType =>
        r match {
          case c: CreateReturnType =>
            JsObject(Map("source" -> JsString(c.source), "submissionType" -> Json.toJson(c.submissionType)))
          case a: AmendReturnType  =>
            JsObject(
              Map(
                "source"         -> JsString(a.source),
                "submissionType" -> Json.toJson(a.submissionType),
                "submissionID"   -> JsString(a.submissionID)
              )
            )
        }
      }
    )
}
