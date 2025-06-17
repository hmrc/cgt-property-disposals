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
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DraftReturnGen.gen
import uk.gov.hmrc.cgtpropertydisposals.models.returns.*

object DraftReturnGen extends LowerPriorityDraftReturnGen {
  given singleDisposalDraftReturnGen: Gen[DraftSingleDisposalReturn] = gen[DraftSingleDisposalReturn]

  given draftReturnGen: Gen[DraftReturn] = Gen.oneOf(
    singleDisposalDraftReturnGen,
    singleIndirectDisposalDraftReturnGen,
    singleMixedUseDraftReturnGen,
    multipleDisposalDraftReturnGen,
    multipleIndirectDisposalDraftReturnGen
  )
}

trait LowerPriorityDraftReturnGen extends GenUtils {

  given multipleDisposalDraftReturnGen: Gen[DraftMultipleDisposalsReturn] = gen[DraftMultipleDisposalsReturn]

  given multipleIndirectDisposalDraftReturnGen: Gen[DraftMultipleIndirectDisposalsReturn] =
    gen[DraftMultipleIndirectDisposalsReturn]

  given singleIndirectDisposalDraftReturnGen: Gen[DraftSingleIndirectDisposalReturn] =
    gen[DraftSingleIndirectDisposalReturn]

  given singleMixedUseDraftReturnGen: Gen[DraftSingleMixedUseDisposalReturn] =
    gen[DraftSingleMixedUseDisposalReturn]
}
