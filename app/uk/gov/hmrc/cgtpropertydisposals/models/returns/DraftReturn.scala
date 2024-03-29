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

import julienrf.json.derived
import play.api.libs.json.{Json, OFormat}
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

  implicit val ukAddressFormat: OFormat[UkAddress] = Json.format[UkAddress]

  implicit val format: OFormat[DraftReturn] = derived.oformat()

}
