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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.AmountInPence

sealed trait SubmitReturnResponse extends Product with Serializable

object SubmitReturnResponse {

  final case class PPDReturnResponseDetails(
    chargeType: String,
    chargeReference: String,
    amount: AmountInPence,
    dueDate: String,
    formBundleNumber: String,
    cgtReferenceNumber: String
  )

  implicit val ppdReturnResponseDetailsFormat: Format[PPDReturnResponseDetails] = Json.format[PPDReturnResponseDetails]

  final case class CreateReturnSuccessful(
    processingDate: String,
    ppdReturnResponseDetails: PPDReturnResponseDetails
  ) extends SubmitReturnResponse

  //final case class AmendReturnSuccessful(cgtReferenceNumber: String) extends SubmitReturnResponse

  implicit val createReturnSuccessfulFormat: Format[CreateReturnSuccessful] = Json.format[CreateReturnSuccessful]

  //implicit val amendReturnSuccessfulFormat: Format[AmendReturnSuccessful] = Json.format[AmendReturnSuccessful]

}
