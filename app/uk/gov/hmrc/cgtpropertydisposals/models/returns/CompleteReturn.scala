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
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExampleCompanyDetailsAnswers.CompleteExampleCompanyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MixedUsePropertyDetailsAnswers.CompleteMixedUsePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.CompleteSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

sealed trait CompleteReturn

object CompleteReturn {

  final case class CompleteMultipleDisposalsReturn(
    triageAnswers: CompleteMultipleDisposalsTriageAnswers,
    examplePropertyDetailsAnswers: CompleteExamplePropertyDetailsAnswers,
    exemptionAndLossesAnswers: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteSingleDisposalReturn(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    propertyAddress: UkAddress,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    reliefDetails: CompleteReliefDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers],
    initialGainOrLoss: Option[AmountInPence],
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteSingleIndirectDisposalReturn(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    companyAddress: Address,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteMultipleIndirectDisposalReturn(
    triageAnswers: CompleteMultipleDisposalsTriageAnswers,
    exampleCompanyDetailsAnswers: CompleteExampleCompanyDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteSingleMixedUseDisposalReturn(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    propertyDetailsAnswers: CompleteMixedUsePropertyDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  implicit class CompleteReturnOps(private val c: CompleteReturn) extends AnyVal {

    def fold[A](
      ifMultiple: CompleteMultipleDisposalsReturn => A,
      ifSingle: CompleteSingleDisposalReturn => A,
      whenSingleIndirect: CompleteSingleIndirectDisposalReturn => A,
      whenMultipleIndirect: CompleteMultipleIndirectDisposalReturn => A,
      whenSingleMixedUse: CompleteSingleMixedUseDisposalReturn => A
    ): A =
      c match {
        case m: CompleteMultipleDisposalsReturn        => ifMultiple(m)
        case s: CompleteSingleDisposalReturn           => ifSingle(s)
        case s: CompleteSingleIndirectDisposalReturn   => whenSingleIndirect(s)
        case m: CompleteMultipleIndirectDisposalReturn => whenMultipleIndirect(m)
        case s: CompleteSingleMixedUseDisposalReturn   => whenSingleMixedUse(s)
      }

  }

  implicit val singleDisposalTriageFormat: Format[CompleteSingleDisposalTriageAnswers]              = Json.format
  implicit val multipleDisposalsTriageFormat: Format[CompleteMultipleDisposalsTriageAnswers]        = Json.format
  implicit val ukAddressFormat: Format[UkAddress]                                                   = Json.format
  implicit val examplePropertyDetailsFormat: Format[CompleteExamplePropertyDetailsAnswers]          = Json.format
  implicit val disposalDetailsFormat: Format[CompleteDisposalDetailsAnswers]                        = Json.format
  implicit val acquisitionDetailsFormat: Format[CompleteAcquisitionDetailsAnswers]                  = Json.format
  implicit val reliefDetailsFormat: Format[CompleteReliefDetailsAnswers]                            = Json.format
  implicit val exemptionAndLossesFormat: Format[CompleteExemptionAndLossesAnswers]                  = Json.format
  implicit val nonCalculatedYearToDateLiabilityFormat: Format[CompleteNonCalculatedYTDAnswers]      = Json.format
  implicit val calculatedYearToDateLiabilityFormat: Format[CompleteCalculatedYTDAnswers]            = Json.format
  implicit val supportingDocumentsAnswersFormat: Format[CompleteSupportingEvidenceAnswers]          = Json.format
  implicit val representeeAnswersFormat: Format[CompleteRepresenteeAnswers]                         = Json.format
  implicit val exampleCompanyDetailsAnswersFormat: Format[CompleteExampleCompanyDetailsAnswers]     = Json.format
  implicit val mixedUsePropertyDetailsAnswersFormat: Format[CompleteMixedUsePropertyDetailsAnswers] = Json.format

  implicit val eitherFormat: Format[Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]] =
    EitherFormat.eitherFormat[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]

  implicit val format: OFormat[CompleteReturn] = {
    singleDisposalTriageFormat
    multipleDisposalsTriageFormat
    ukAddressFormat
    examplePropertyDetailsFormat
    disposalDetailsFormat
    disposalDetailsFormat
    acquisitionDetailsFormat
    reliefDetailsFormat
    exemptionAndLossesFormat
    nonCalculatedYearToDateLiabilityFormat
    calculatedYearToDateLiabilityFormat
    supportingDocumentsAnswersFormat
    representeeAnswersFormat
    exampleCompanyDetailsAnswersFormat
    mixedUsePropertyDetailsAnswersFormat
    derived.oformat()
  }
}
