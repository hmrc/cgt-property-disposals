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
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesFinancialTransaction
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.*
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails, SingleMixedUseDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DesReturnSummary

object DesReturnsGen extends GenUtils {
  given desReturnDetailsGen: Gen[DesReturnDetails] = gen[DesReturnDetails]

  given desSingleDisposalDetailsGen: Gen[SingleDisposalDetails] = gen[SingleDisposalDetails]

  given desMultipleDisposalsDetailsGen: Gen[MultipleDisposalDetails] =
    gen[MultipleDisposalDetails]

  given desSingleMixedUseDisposalsDetailsGen: Gen[SingleMixedUseDisposalDetails] =
    gen[SingleMixedUseDisposalDetails]

  given returnDetailsGen: Gen[ReturnDetails] = gen[ReturnDetails]

  given desReliefDetailsGen: Gen[ReliefDetails] = gen[ReliefDetails]

  given representedPersonDetailsGen: Gen[RepresentedPersonDetails] = gen[RepresentedPersonDetails]

  given incomeAllowanceDetailsGen: Gen[IncomeAllowanceDetails] = gen[IncomeAllowanceDetails]

  given desReturnSummaryGen: Gen[DesReturnSummary] = gen[DesReturnSummary]

  given desFinancialTransactionGen: Gen[DesFinancialTransaction] = gen[DesFinancialTransaction]

  given desSubmitReturnRequestGen: Gen[DesSubmitReturnRequest] = gen[DesSubmitReturnRequest]

  given createReturnTypeGen: Gen[CreateReturnType] = gen[CreateReturnType]

  given amendReturnTypeGen: Gen[AmendReturnType] = gen[AmendReturnType]

}
