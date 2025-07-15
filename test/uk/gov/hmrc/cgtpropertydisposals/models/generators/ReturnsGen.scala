/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.generators

import io.github.martinhh.derived.scalacheck.given
import org.scalacheck.Gen
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYearConfig
import uk.gov.hmrc.cgtpropertydisposals.models.returns.*
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExampleCompanyDetailsAnswers.CompleteExampleCompanyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MixedUsePropertyDetailsAnswers.CompleteMixedUsePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.OtherReliefsOption.OtherReliefs
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.{CompleteSupportingEvidenceAnswers, SupportingEvidence}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers
import uk.gov.hmrc.time.TaxYear

object ReturnsGen extends GenUtils {

  given displayReturnGen: Gen[DisplayReturn] = gen[DisplayReturn]

  given hasEstimatedDetailsWithCalculatedTaxDueGen: Gen[HasEstimatedDetailsWithCalculatedTaxDue] =
    gen[HasEstimatedDetailsWithCalculatedTaxDue]

  given otherReliefsGen: Gen[OtherReliefs] = gen[OtherReliefs]

  given calculatedTaxDueGen: Gen[CalculatedTaxDue] = gen[CalculatedTaxDue]

  given gainCalculatedTaxDueGen: Gen[GainCalculatedTaxDue] = gen[GainCalculatedTaxDue]

  given listReturnResponseGen: Gen[ListReturnsResponse] =
    gen[ListReturnsResponse]

  given taxYearGen: Gen[TaxYear] = gen[TaxYear]

  given taxYearConfigGen: Gen[TaxYearConfig] = gen[TaxYearConfig]

  given disposalDateGen: Gen[DisposalDate] = gen[DisposalDate]

  given calculateCgtTaxDueRequestGen: Gen[CalculateCgtTaxDueRequest] =
    gen[CalculateCgtTaxDueRequest]

  given returnSummaryGen: Gen[ReturnSummary] = gen[ReturnSummary]

  given assetTypeGen: Gen[AssetType] = gen[AssetType]

  given mandatoryEvidenceGen: Gen[MandatoryEvidence] = gen[MandatoryEvidence]

  given supportingEvidenceGen: Gen[SupportingEvidence] = gen[SupportingEvidence]

  given representeeDetailsGen: Gen[RepresenteeDetails] = gen[RepresenteeDetails]

  given representeeContactDetailsGen: Gen[RepresenteeContactDetails] = gen[RepresenteeContactDetails]

  given completeRepresenteeAnswersGen: Gen[CompleteRepresenteeAnswers] = gen[CompleteRepresenteeAnswers]

  given completeExampleCompanyDetailsAnswersGen: Gen[CompleteExampleCompanyDetailsAnswers] =
    gen[CompleteExampleCompanyDetailsAnswers]

  given completeSupportingEvidenceAnswersGen: Gen[CompleteSupportingEvidenceAnswers] =
    gen[CompleteSupportingEvidenceAnswers]

  given amendReturnDataGen: Gen[AmendReturnData] = gen[AmendReturnData]

  given completeReturnWithSummaryGen: Gen[CompleteReturnWithSummary] = gen[CompleteReturnWithSummary]

}
