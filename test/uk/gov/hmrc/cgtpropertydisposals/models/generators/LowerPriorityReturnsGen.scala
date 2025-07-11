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

import io.github.martinhh.derived.scalacheck.anyGivenArbitrary
import org.scalacheck.Gen
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.NonGainCalculatedTaxDue
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MixedUsePropertyDetailsAnswers.CompleteMixedUsePropertyDetailsAnswers

object LowerPriorityReturnsGen extends GenUtils {
  given nonGainCalculatedTaxDueGen: Gen[NonGainCalculatedTaxDue] = gen[NonGainCalculatedTaxDue]

  given completeMultipleDisposalReturnGen: Gen[CompleteMultipleDisposalsReturn] =
    gen[CompleteMultipleDisposalsReturn]

  given completeSingleIndirectDisposalReturnGen: Gen[CompleteSingleIndirectDisposalReturn] =
    gen[CompleteSingleIndirectDisposalReturn]

  given completeMultipleIndirectDisposalReturnGen: Gen[CompleteMultipleIndirectDisposalReturn] =
    gen[CompleteMultipleIndirectDisposalReturn]

  given completeSingleMixedUseDisposalReturnGen: Gen[CompleteSingleMixedUseDisposalReturn] =
    gen[CompleteSingleMixedUseDisposalReturn]

  given completeMixedUsePropertyDetailsGen: Gen[CompleteMixedUsePropertyDetailsAnswers] =
    gen[CompleteMixedUsePropertyDetailsAnswers]

}
