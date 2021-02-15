/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{RepresenteeDetails, SubmitReturnRequest}

final case class DesReturnDetails(
  returnType: DesReturnType,
  returnDetails: ReturnDetails,
  representedPersonDetails: Option[RepresentedPersonDetails],
  disposalDetails: List[DisposalDetails],
  lossSummaryDetails: LossSummaryDetails,
  incomeAllowanceDetails: IncomeAllowanceDetails,
  reliefDetails: Option[ReliefDetails]
)

object DesReturnDetails {

  def apply(
    submitReturnRequest: SubmitReturnRequest,
    representeeDetails: Option[RepresenteeDetails]
  ): DesReturnDetails = {
    val completeReturn         = submitReturnRequest.completeReturn
    val returnDetails          = ReturnDetails(submitReturnRequest)
    val lossSummaryDetails     = LossSummaryDetails(
      completeReturn.fold(
        _.exemptionAndLossesAnswers,
        _.exemptionsAndLossesDetails,
        _.exemptionsAndLossesDetails,
        _.exemptionsAndLossesDetails,
        _.exemptionsAndLossesDetails
      )
    )
    val reliefDetails          =
      completeReturn.fold(_ => None, s => Some(ReliefDetails.fromCompleteReturn(s)), _ => None, _ => None, _ => None)
    val incomeAllowanceDetails = IncomeAllowanceDetails(completeReturn)
    val disposalDetails        = DisposalDetails(completeReturn)

    DesReturnDetails(
      returnType = DesReturnType(submitReturnRequest),
      returnDetails = returnDetails,
      representedPersonDetails = representeeDetails.map(RepresentedPersonDetails(_)),
      disposalDetails = List(disposalDetails),
      lossSummaryDetails = lossSummaryDetails,
      incomeAllowanceDetails = incomeAllowanceDetails,
      reliefDetails = reliefDetails
    )
  }

  implicit val ppdReturnDetailsFormat: OFormat[DesReturnDetails] = Json.format[DesReturnDetails]

}
