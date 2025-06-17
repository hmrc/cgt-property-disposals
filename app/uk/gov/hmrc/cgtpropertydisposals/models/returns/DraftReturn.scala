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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json._
import cats.Eq
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

import java.time.LocalDate
import java.util.UUID

sealed trait DraftReturn extends Product with Serializable {
  val id: UUID
  val exemptionAndLossesAnswers: Option[ExemptionAndLossesAnswers]
  val yearToDateLiabilityAnswers: Option[YearToDateLiabilityAnswers]
  val supportingEvidenceAnswers: Option[SupportingEvidenceAnswers]
}

final case class DraftSingleDisposalReturn(
  id: UUID,
  triageAnswers: SingleDisposalTriageAnswers,
  propertyAddress: Option[UkAddress],
  disposalDetailsAnswers: Option[DisposalDetailsAnswers],
  acquisitionDetailsAnswers: Option[AcquisitionDetailsAnswers],
  reliefDetailsAnswers: Option[ReliefDetailsAnswers],
  exemptionAndLossesAnswers: Option[ExemptionAndLossesAnswers],
  yearToDateLiabilityAnswers: Option[YearToDateLiabilityAnswers],
  initialGainOrLoss: Option[AmountInPence],
  supportingEvidenceAnswers: Option[SupportingEvidenceAnswers],
  representeeAnswers: Option[RepresenteeAnswers],
  gainOrLossAfterReliefs: Option[AmountInPence],
  lastUpdatedDate: LocalDate
) extends DraftReturn

final case class DraftMultipleDisposalsReturn(
  id: UUID,
  triageAnswers: MultipleDisposalsTriageAnswers,
  examplePropertyDetailsAnswers: Option[ExamplePropertyDetailsAnswers],
  exemptionAndLossesAnswers: Option[ExemptionAndLossesAnswers],
  yearToDateLiabilityAnswers: Option[YearToDateLiabilityAnswers],
  supportingEvidenceAnswers: Option[SupportingEvidenceAnswers],
  representeeAnswers: Option[RepresenteeAnswers],
  gainOrLossAfterReliefs: Option[AmountInPence],
  lastUpdatedDate: LocalDate
) extends DraftReturn

final case class DraftSingleIndirectDisposalReturn(
  id: UUID,
  triageAnswers: SingleDisposalTriageAnswers,
  companyAddress: Option[Address],
  disposalDetailsAnswers: Option[DisposalDetailsAnswers],
  acquisitionDetailsAnswers: Option[AcquisitionDetailsAnswers],
  exemptionAndLossesAnswers: Option[ExemptionAndLossesAnswers],
  yearToDateLiabilityAnswers: Option[YearToDateLiabilityAnswers],
  supportingEvidenceAnswers: Option[SupportingEvidenceAnswers],
  representeeAnswers: Option[RepresenteeAnswers],
  gainOrLossAfterReliefs: Option[AmountInPence],
  lastUpdatedDate: LocalDate
) extends DraftReturn

final case class DraftMultipleIndirectDisposalsReturn(
  id: UUID,
  triageAnswers: MultipleDisposalsTriageAnswers,
  exampleCompanyDetailsAnswers: Option[ExampleCompanyDetailsAnswers],
  exemptionAndLossesAnswers: Option[ExemptionAndLossesAnswers],
  yearToDateLiabilityAnswers: Option[YearToDateLiabilityAnswers],
  supportingEvidenceAnswers: Option[SupportingEvidenceAnswers],
  representeeAnswers: Option[RepresenteeAnswers],
  gainOrLossAfterReliefs: Option[AmountInPence],
  lastUpdatedDate: LocalDate
) extends DraftReturn

final case class DraftSingleMixedUseDisposalReturn(
  id: UUID,
  triageAnswers: SingleDisposalTriageAnswers,
  mixedUsePropertyDetailsAnswers: Option[MixedUsePropertyDetailsAnswers],
  exemptionAndLossesAnswers: Option[ExemptionAndLossesAnswers],
  yearToDateLiabilityAnswers: Option[YearToDateLiabilityAnswers],
  supportingEvidenceAnswers: Option[SupportingEvidenceAnswers],
  representeeAnswers: Option[RepresenteeAnswers],
  gainOrLossAfterReliefs: Option[AmountInPence],
  lastUpdatedDate: LocalDate
) extends DraftReturn

object DraftReturn {

  implicit val eq: Eq[DraftReturn] = Eq.fromUniversalEquals

  implicit val ukAddressFormat: OFormat[UkAddress] = Json.format[UkAddress]

  implicit val singleReturnFormat: OFormat[DraftSingleDisposalReturn]                      = Json.format[DraftSingleDisposalReturn]
  implicit val singleIndirectReturnFormat: OFormat[DraftSingleIndirectDisposalReturn]      =
    Json.format[DraftSingleIndirectDisposalReturn]
  implicit val singleMixedReturnFormat: OFormat[DraftSingleMixedUseDisposalReturn]         =
    Json.format[DraftSingleMixedUseDisposalReturn]
  implicit val multipleReturnFormat: OFormat[DraftMultipleDisposalsReturn]                 = Json.format[DraftMultipleDisposalsReturn]
  implicit val multipleIndirectReturnFormat: OFormat[DraftMultipleIndirectDisposalsReturn] =
    Json.format[DraftMultipleIndirectDisposalsReturn]

  implicit val format: OFormat[DraftReturn] = new OFormat[DraftReturn] {
    override def reads(json: JsValue): JsResult[DraftReturn] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("DraftSingleDisposalReturn", value)            =>
            value.validate[DraftSingleDisposalReturn]
          case ("DraftMultipleDisposalsReturn", value)         =>
            value.validate[DraftMultipleDisposalsReturn]
          case ("DraftSingleIndirectDisposalReturn", value)    =>
            value.validate[DraftSingleIndirectDisposalReturn]
          case ("DraftMultipleIndirectDisposalsReturn", value) =>
            value.validate[DraftMultipleIndirectDisposalsReturn]
          case ("DraftSingleMixedUseDisposalReturn", value)    =>
            value.validate[DraftSingleMixedUseDisposalReturn]
          case (other, _)                                      =>
            JsError(s"Unrecognized DraftReturn type: $other")
        }
      case _                                    =>
        JsError("Expected a DraftReturn wrapper object with a single entry")
    }

    override def writes(d: DraftReturn): JsObject = d match {
      case s: DraftSingleDisposalReturn            =>
        Json.obj("DraftSingleDisposalReturn" -> Json.toJson(s))
      case m: DraftMultipleDisposalsReturn         =>
        Json.obj("DraftMultipleDisposalsReturn" -> Json.toJson(m))
      case s: DraftSingleIndirectDisposalReturn    =>
        Json.obj("DraftSingleIndirectDisposalReturn" -> Json.toJson(s))
      case m: DraftMultipleIndirectDisposalsReturn =>
        Json.obj("DraftMultipleIndirectDisposalsReturn" -> Json.toJson(m))
      case s: DraftSingleMixedUseDisposalReturn    =>
        Json.obj("DraftSingleMixedUseDisposalReturn" -> Json.toJson(s))
    }
  }

}
