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
import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.gen
import uk.gov.hmrc.cgtpropertydisposals.models.generators.ReturnsGen.supportingEvidenceGen
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AcquisitionDetailsAnswers, DisposalDetailsAnswers, DraftMultipleDisposalsReturn, DraftMultipleIndirectDisposalsReturn, DraftReturn, DraftSingleDisposalReturn, DraftSingleIndirectDisposalReturn, DraftSingleMixedUseDisposalReturn, ExemptionAndLossesAnswers, ReliefDetailsAnswers, RepresenteeAnswers, SingleDisposalTriageAnswers, SupportingEvidenceAnswers, YearToDateLiabilityAnswers}

import java.time.LocalDate

object DraftReturnGen extends HigherPriorityDraftReturnGen with GenUtils

trait HigherPriorityDraftReturnGen extends LowerPriorityDraftReturnGen {

  given singleDisposalDraftReturnGen: Gen[DraftSingleDisposalReturn] = singleDisposalDraftReturnGen2

  given draftReturnGen: Gen[DraftReturn] = gen[DraftReturn]
}

trait LowerPriorityDraftReturnGen extends GenUtils {

  given multipleDisposalDraftReturnGen: Gen[DraftMultipleDisposalsReturn] = gen[DraftMultipleDisposalsReturn]

  given multipleIndirectDisposalDraftReturnGen: Gen[DraftMultipleIndirectDisposalsReturn] =
    gen[DraftMultipleIndirectDisposalsReturn]

  given singleIndirectDisposalDraftReturnGen: Gen[DraftSingleIndirectDisposalReturn] =
    gen[DraftSingleIndirectDisposalReturn]

  given singleMixedUseDraftReturnGen: Gen[DraftSingleMixedUseDisposalReturn] =
    gen[DraftSingleMixedUseDisposalReturn]

  given singleDisposalDraftReturnGen2: Gen[DraftSingleDisposalReturn] =
    for {
      id                         <- Gen.uuid
      triageAnswers              <- gen[SingleDisposalTriageAnswers]
      propertyAddress            <- Gen.option(AddressGen.ukAddressGen)
      disposalDetailsAnswers     <- Gen.option(gen[DisposalDetailsAnswers])
      acquisitionDetailsAnswers  <- Gen.option(gen[AcquisitionDetailsAnswers])
      reliefDetailsAnswers       <- Gen.option(gen[ReliefDetailsAnswers])
      exemptionAndLossesAnswers  <- Gen.option(gen[ExemptionAndLossesAnswers])
      yearToDateLiabilityAnswers <- Gen.option(gen[YearToDateLiabilityAnswers])
      initialGainOrLoss          <- Gen.option(MoneyGen.amountInPenceGen)
      supportingEvidenceAnswers  <- Gen.option(gen[SupportingEvidenceAnswers])
      representeeAnswers         <- Gen.option(gen[RepresenteeAnswers])
      gainOrLossAfterReliefs     <- Gen.option(MoneyGen.amountInPenceGen)
      lastUpdatedDate            <- Arbitrary.arbitrary[LocalDate]
    } yield DraftSingleDisposalReturn(
      id,
      triageAnswers,
      propertyAddress,
      disposalDetailsAnswers,
      acquisitionDetailsAnswers,
      reliefDetailsAnswers,
      exemptionAndLossesAnswers,
      yearToDateLiabilityAnswers,
      initialGainOrLoss,
      supportingEvidenceAnswers,
      representeeAnswers,
      gainOrLossAfterReliefs,
      lastUpdatedDate
    )
}
