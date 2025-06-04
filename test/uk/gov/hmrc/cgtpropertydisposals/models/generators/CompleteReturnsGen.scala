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
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.generators.ReturnsGen.completeExampleCompanyDetailsAnswersGen
import uk.gov.hmrc.cgtpropertydisposals.models.returns.*
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.*
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MixedUsePropertyDetailsAnswers.CompleteMixedUsePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.CompleteSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

object CompleteReturnsGen extends LowerPriorityCompleteReturnGen {



  given completeSingleDisposalReturnGen: Gen[CompleteSingleDisposalReturn] =
    for {
      triageAnswers <- completeSingleDisposalTriageAnswersGen
      propertyAddress <- AddressGen.ukAddressGen
      disposalDetails <- disposalDetails
      acquisitionDetails <- acquisitionDetails
      reliefDetails <- completeReliefDetailsAnswersGen
      exemptionsAndLossesDetails <- exemptionAndLossesAnswersGen
      chosenYearToDateLiabilityAnswers <-  Gen.either(yearToDateLiabilityAnswersGen, completeCalculatedYTDLiabilityAnswersGen)
      supportingDocumentAnswers <- supportingDocumentAnswersGen
      initialGainOrLoss <- Gen.option(MoneyGen.amountInPenceGen)
      representeeAnswers <- Gen.option(representeeAnswersGen)
      gainOrLossAfterReliefs <- Gen.option(MoneyGen.amountInPenceGen)
      hasAttachments <- Generators.booleanGen
    } yield CompleteSingleDisposalReturn(
      triageAnswers,
      propertyAddress,
      disposalDetails,
      acquisitionDetails,
      reliefDetails,
      exemptionsAndLossesDetails,
      chosenYearToDateLiabilityAnswers,
      initialGainOrLoss,
      supportingDocumentAnswers,
      representeeAnswers,
      gainOrLossAfterReliefs,
      hasAttachments
    )

}

trait LowerPriorityCompleteReturnGen extends GenUtils {

  given completeSingleDisposalTriageAnswersGen: Gen[CompleteSingleDisposalTriageAnswers] =
    gen[CompleteSingleDisposalTriageAnswers]

  given completeMultipleDisposalsTriageAnswersGen: Gen[CompleteMultipleDisposalsTriageAnswers] =
    gen[CompleteMultipleDisposalsTriageAnswers]

  given completeExamplePropertyDetailsAnswersGen: Gen[CompleteExamplePropertyDetailsAnswers] =
    gen[CompleteExamplePropertyDetailsAnswers]

  given disposalDetails: Gen[CompleteDisposalDetailsAnswers] = gen[CompleteDisposalDetailsAnswers]

  given acquisitionDetails: Gen[CompleteAcquisitionDetailsAnswers] =
    gen[CompleteAcquisitionDetailsAnswers]


  given completeReliefDetailsAnswersGen: Gen[CompleteReliefDetailsAnswers] = gen[CompleteReliefDetailsAnswers]

  given exemptionAndLossesAnswersGen: Gen[CompleteExemptionAndLossesAnswers] = gen[CompleteExemptionAndLossesAnswers]

  given yearToDateLiabilityAnswersGen: Gen[CompleteNonCalculatedYTDAnswers] = gen[CompleteNonCalculatedYTDAnswers]

  given supportingDocumentAnswersGen: Gen[CompleteSupportingEvidenceAnswers] = gen[CompleteSupportingEvidenceAnswers]

  given representeeAnswersGen: Gen[CompleteRepresenteeAnswers] = gen[CompleteRepresenteeAnswers]

  given completeCalculatedYTDLiabilityAnswersGen: Gen[CompleteCalculatedYTDAnswers] = gen[CompleteCalculatedYTDAnswers]
  given completeMixedUsePropertyDetailsGen: Gen[CompleteMixedUsePropertyDetailsAnswers] =
    gen[CompleteMixedUsePropertyDetailsAnswers]

  implicit val completeMultipleDisposalsReturnGen: Gen[CompleteMultipleDisposalsReturn] = for {
    triageAnswers <- completeMultipleDisposalsTriageAnswersGen
    examplePropertyDetailsAnswers <- completeExamplePropertyDetailsAnswersGen
    exemptionAndLossesAnswers <- exemptionAndLossesAnswersGen
    yearToDateLiabilityAnswers <- yearToDateLiabilityAnswersGen
    supportingDocumentAnswers <- supportingDocumentAnswersGen
    representeeAnswers <- Gen.option(representeeAnswersGen)
    gainOrLossAfterReliefs <- Gen.option(MoneyGen.amountInPenceGen)
    hasAttachments <- Generators.booleanGen
  } yield CompleteMultipleDisposalsReturn(
    triageAnswers,
    examplePropertyDetailsAnswers,
    exemptionAndLossesAnswers,
    yearToDateLiabilityAnswers,
    supportingDocumentAnswers,
    representeeAnswers,
    gainOrLossAfterReliefs,
    hasAttachments
  )

  implicit val completeSingleIndirectDisposalReturnGen: Gen[CompleteSingleIndirectDisposalReturn] =
    for {
      triageAnswers <- completeSingleDisposalTriageAnswersGen
      companyAddress <- AddressGen.addressGen
      disposalDetails <- disposalDetails
      acquisitionDetails <- acquisitionDetails
      exemptionsAndLossesDetails <- exemptionAndLossesAnswersGen
      yearToDateLiabilityAnswers <- yearToDateLiabilityAnswersGen
      supportingDocumentAnswers <- supportingDocumentAnswersGen
      representeeAnswers <- Gen.option(representeeAnswersGen)
      gainOrLossAfterReliefs <- Gen.option(MoneyGen.amountInPenceGen)
      hasAttachments <- Generators.booleanGen
    } yield CompleteSingleIndirectDisposalReturn(
      triageAnswers,
      companyAddress,
      disposalDetails,
      acquisitionDetails,
      exemptionsAndLossesDetails,
      yearToDateLiabilityAnswers,
      supportingDocumentAnswers,
      representeeAnswers,
      gainOrLossAfterReliefs,
      hasAttachments
    )

  implicit val completeMultipleIndirectDisposalReturnGen: Gen[CompleteMultipleIndirectDisposalReturn] = for {
    triageAnswers <- completeMultipleDisposalsTriageAnswersGen
    exampleCompanyDetailsAnswers <- completeExampleCompanyDetailsAnswersGen
    exemptionsAndLossesDetails <- exemptionAndLossesAnswersGen
    yearToDateLiabilityAnswers <- yearToDateLiabilityAnswersGen
    supportingDocumentAnswers <- supportingDocumentAnswersGen
    representeeAnswers <- Gen.option(representeeAnswersGen)
    gainOrLossAfterReliefs <- Gen.option(MoneyGen.amountInPenceGen)
    hasAttachments <- Generators.booleanGen
  } yield CompleteMultipleIndirectDisposalReturn(
    triageAnswers,
    exampleCompanyDetailsAnswers,
    exemptionsAndLossesDetails,
    yearToDateLiabilityAnswers,
    supportingDocumentAnswers,
    representeeAnswers,
    gainOrLossAfterReliefs,
    hasAttachments
  )

  implicit val completeSingleMixedUseDisposalReturnGen: Gen[CompleteSingleMixedUseDisposalReturn] =
    for {
      triageAnswers <- completeSingleDisposalTriageAnswersGen
      propertyDetailsAnswers <- completeMixedUsePropertyDetailsGen
      exemptionsAndLossesDetails <- exemptionAndLossesAnswersGen
      yearToDateLiabilityAnswers <- yearToDateLiabilityAnswersGen
      supportingDocumentAnswers <- supportingDocumentAnswersGen
      representeeAnswers <- Gen.option(representeeAnswersGen)
      gainOrLossAfterReliefs <- Gen.option(MoneyGen.amountInPenceGen)
      hasAttachments <- Generators.booleanGen
    } yield CompleteSingleMixedUseDisposalReturn(
      triageAnswers,
      propertyDetailsAnswers,
      exemptionsAndLossesDetails,
      yearToDateLiabilityAnswers,
      supportingDocumentAnswers,
      representeeAnswers,
      gainOrLossAfterReliefs,
      hasAttachments
    )

}
