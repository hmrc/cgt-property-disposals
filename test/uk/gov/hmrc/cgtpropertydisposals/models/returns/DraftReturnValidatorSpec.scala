/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DraftReturnGen.{multipleDisposalDraftReturnGen, singleDisposalDraftReturnGen}
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.{CompleteMultipleDisposalsTriageAnswers, IncompleteMultipleDisposalsTriageAnswers}
import uk.gov.hmrc.cgtpropertydisposals.models.generators.CompleteReturnsGen.completeMultipleDisposalsTriageAnswersGen

class DraftReturnValidatorSpec extends AnyWordSpec with Matchers {

  def incompleteTriage(n: Option[Int]) =
    IncompleteMultipleDisposalsTriageAnswers(
      individualUserType = None,
      numberOfProperties = n,
      wasAUKResident = None,
      countryOfResidence = None,
      wereAllPropertiesResidential = None,
      assetTypes = None,
      taxYearExchanged = None,
      taxYear = None,
      alreadySentSelfAssessment = None,
      completionDate = None
    )

  def completeDraft(n: Int): DraftMultipleDisposalsReturn =
    Generators
      .sample[DraftMultipleDisposalsReturn]
      .copy(
        triageAnswers = Generators
          .sample[CompleteMultipleDisposalsTriageAnswers]
          .copy(numberOfProperties = n)
      )

  def incompleteDraft(n: Option[Int]): DraftMultipleDisposalsReturn =
    Generators
      .sample[DraftMultipleDisposalsReturn]
      .copy(triageAnswers = incompleteTriage(n))

  "DraftReturnValidator" should {
    "accept boundary values on complete answers" in {
      List(2, 3, 998, 999).foreach { n =>
        DraftReturnValidator.validateNumberOfProperties(completeDraft(n)) shouldBe Right(())
      }
    }

    "reject out of range values on complete answers" in {
      List(Int.MinValue, -1, 0, 1000, Int.MaxValue).foreach { n =>
        DraftReturnValidator.validateNumberOfProperties(completeDraft(n)).isLeft shouldBe true
      }
    }

    "accept None on incomplete answers" in {
      DraftReturnValidator.validateNumberOfProperties(incompleteDraft(None)) shouldBe Right(())
    }

    "reject out of range values on incomplete answers" in {
      List(-1, 0, 1000).foreach { n =>
        DraftReturnValidator.validateNumberOfProperties(incompleteDraft(Some(n))).isLeft shouldBe true
      }
    }

    "not apply to other draft return types" in {
      DraftReturnValidator.validateNumberOfProperties(Generators.sample[DraftSingleDisposalReturn]) shouldBe Right(())
    }
  }
}
