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

package uk.gov.hmrc.cgtpropertydisposals.service.returns.transformers

import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{DesFinancialTransaction, DesFinancialTransactionItem}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.ChargeType.{DeltaCharge, UkResidentReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.finance._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AmendReturnData, CompleteReturnWithSummary, ReturnSummary, SubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.{DesCharge, DesReturnSummary}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.TaxYearService

import java.time.LocalDate

class ReturnSummaryListTransformerServiceImplSpec
    extends AnyWordSpec
    with Matchers
    with MockFactory
    with OneInstancePerTest {

  val mockTaxYearService = mock[TaxYearService]

  (mockTaxYearService.getTaxYear(_: LocalDate)).expects(*).returning(Some(sample[TaxYear])).anyNumberOfTimes()

  val transformer = new ReturnSummaryListTransformerServiceImpl(mockTaxYearService)

  "ReturnSummaryListTransformerServiceImpl" when {

    "converting from des data to a return list" must {

      "return an error" when {
        val ukAddress = sample[UkAddress]

        val validDesCharge = DesCharge(
          "CGT PPD Return UK Resident",
          LocalDate.now(),
          "reference"
        )

        val validDesReturnSummary = sample[DesReturnSummary].copy(
          propertyAddress = Address.toAddressDetails(ukAddress),
          charges = Some(List(validDesCharge))
        )

        val validDesFinancialTransaction = sample[DesFinancialTransaction].copy(
          chargeReference = validDesCharge.chargeReference,
          items =
            Some(List(DesFinancialTransactionItem(Some(BigDecimal(1)), None, None, None, Some(validDesCharge.dueDate))))
        )

        "there is a charge in a return summary with a charge type which is not recognised" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary.copy(
                charges = Some(
                  List(
                    validDesCharge.copy(chargeReference = "???")
                  )
                )
              )
            ),
            List(validDesFinancialTransaction),
            List.empty
          )

          result.isLeft shouldBe true
        }

        "no financial data can be found for a charge in the return summary list" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary
            ),
            List.empty,
            List.empty
          )

          result.isLeft shouldBe true
        }

        "some but not all payment information is found" in {
          val date           = LocalDate.ofEpochDay(0L)
          val amount         = BigDecimal(1)
          val paymentMethod  = "CHAPS"
          val clearingReason = "Write-Off"

          List(
            (None, Some(paymentMethod), Some(date), Some(clearingReason)),
            (Some(amount), Some(paymentMethod), None, Some(clearingReason)),
            (Some(amount), None, None, Some(clearingReason)),
            (None, Some(paymentMethod), None, None)
          ).foreach { case (amount, paymentMethod, date, clearingReason) =>
            withClue(
              s"For (amount, paymentMethod, date, clearingReason) = ($amount, $paymentMethod, $date, $clearingReason): "
            ) {
              val result = transformer.toReturnSummaryList(
                List(
                  validDesReturnSummary
                ),
                List(
                  validDesFinancialTransaction.copy(
                    items = Some(
                      List(DesFinancialTransactionItem(amount, paymentMethod, date, clearingReason, None))
                    )
                  )
                ),
                List.empty
              )

              result.isLeft shouldBe true
            }

          }

        }

        "an address is found which is a non-uk address" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary.copy(
                propertyAddress = Address.toAddressDetails(sample[NonUkAddress])
              )
            ),
            List(validDesFinancialTransaction),
            List.empty
          )

          result.isLeft shouldBe true
        }

        "a return summary is found without a UkResidentReturn or NonUkResidentReturn charge type" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary.copy(
                charges = Some(
                  List(
                    validDesCharge.copy(
                      chargeDescription = "CGT PPD 6 Mth LFP"
                    )
                  )
                )
              )
            ),
            List(validDesFinancialTransaction),
            List.empty
          )

          result.isLeft shouldBe true

        }

        "an financial transaction item cannot be found with a due date which is the same as the due date in the return summary" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary
            ),
            List(
              sample[DesFinancialTransaction].copy(
                chargeReference = validDesCharge.chargeReference,
                items = Some(
                  List(
                    DesFinancialTransactionItem(
                      Some(BigDecimal(1)),
                      None,
                      None,
                      None,
                      Some(validDesCharge.dueDate.plusDays(1L))
                    )
                  )
                )
              )
            ),
            List.empty
          )

          result.isLeft shouldBe true
        }

        "there exist more than two charges with a main return charge type in one return" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary.copy(
                charges = Some(
                  List(
                    validDesCharge,
                    validDesCharge.copy(dueDate = LocalDate.now().plusDays(1L)),
                    validDesCharge.copy(dueDate = LocalDate.now().plusDays(2L))
                  )
                )
              )
            ),
            List(
              sample[DesFinancialTransaction].copy(
                chargeReference = validDesCharge.chargeReference,
                items = Some(
                  List(
                    DesFinancialTransactionItem(Some(BigDecimal(1)), None, None, None, Some(validDesCharge.dueDate))
                  )
                )
              ),
              sample[DesFinancialTransaction].copy(
                chargeReference = validDesCharge.chargeReference,
                items = Some(
                  List(
                    DesFinancialTransactionItem(
                      Some(BigDecimal(1)),
                      None,
                      None,
                      None,
                      Some(LocalDate.now().plusDays(1L))
                    )
                  )
                )
              ),
              sample[DesFinancialTransaction].copy(
                chargeReference = validDesCharge.chargeReference,
                items = Some(
                  List(
                    DesFinancialTransactionItem(
                      Some(BigDecimal(1)),
                      None,
                      None,
                      None,
                      Some(LocalDate.now().plusDays(2L))
                    )
                  )
                )
              )
            ),
            List.empty
          )

          result.isLeft shouldBe true
        }

      }

      "transform the data correctly" when {
        val (ukAddress1, ukAddress2) = sample[UkAddress] -> sample[UkAddress]

        val (mainDueDate1, mainDueDate2, penaltyChargeDueDate) =
          (LocalDate.ofEpochDay(1), LocalDate.ofEpochDay(2), LocalDate.ofEpochDay(3))

        val (mainCharge1, mainCharge2, penaltyCharge) =
          (
            DesCharge(
              "CGT PPD Return UK Resident",
              mainDueDate1,
              "reference1"
            ),
            DesCharge(
              "CGT PPD Return UK Resident",
              mainDueDate2,
              "reference2"
            ),
            DesCharge(
              "CGT PPD Late Filing Penalty",
              penaltyChargeDueDate,
              "reference3"
            )
          )

        val (validDesReturnSummary1, validDesReturnSummary2) =
          (
            sample[DesReturnSummary].copy(
              propertyAddress = Address.toAddressDetails(ukAddress1),
              charges = Some(List(mainCharge1, mainCharge1))
            ),
            sample[DesReturnSummary].copy(
              propertyAddress = Address.toAddressDetails(ukAddress2),
              charges = Some(List(mainCharge2, penaltyCharge))
            )
          )

        val mainCharge1FinancialTransaction = sample[DesFinancialTransaction].copy(
          chargeReference = mainCharge1.chargeReference,
          items = Some(
            List(
              DesFinancialTransactionItem(
                Some(BigDecimal(1)),
                None,
                None,
                None,
                Some(mainDueDate1)
              )
            )
          )
        )

        val (mainCharge2PaymentAmount, mainCharge2PaymentMethod, mainCharge2PaymentDate, mainCharge2ClearingReason) =
          (BigDecimal(10), "CHAPS", LocalDate.ofEpochDay(5L), "Reversal")

        val mainCharge2FinancialTransaction = sample[DesFinancialTransaction].copy(
          chargeReference = mainCharge2.chargeReference,
          items = Some(
            List(
              DesFinancialTransactionItem(
                Some(mainCharge2PaymentAmount),
                Some(mainCharge2PaymentMethod),
                Some(mainCharge2PaymentDate),
                Some(mainCharge2ClearingReason),
                None
              ),
              DesFinancialTransactionItem(
                Some(BigDecimal(1)),
                None,
                None,
                None,
                Some(mainDueDate2)
              )
            )
          )
        )

        val penaltyChargeFinancialTransaction = sample[DesFinancialTransaction].copy(
          chargeReference = penaltyCharge.chargeReference,
          items =
            Some(List(DesFinancialTransactionItem(Some(BigDecimal(1)), None, None, None, Some(penaltyChargeDueDate))))
        )

        val result = transformer.toReturnSummaryList(
          List(validDesReturnSummary1, validDesReturnSummary2),
          List(mainCharge1FinancialTransaction, mainCharge2FinancialTransaction, penaltyChargeFinancialTransaction),
          List.empty
        )

        "there is a recently amended return and matches one of the returns" in {
          val submitReturnRequest =
            sample[SubmitReturnRequest].copy(amendReturnData =
              Some(
                sample[AmendReturnData].copy(
                  originalReturn = sample[CompleteReturnWithSummary].copy(
                    summary = sample[ReturnSummary].copy(submissionId = validDesReturnSummary1.submissionId)
                  )
                )
              )
            )

          val r1 = transformer.toReturnSummaryList(
            List(validDesReturnSummary1),
            List(mainCharge1FinancialTransaction, mainCharge2FinancialTransaction, penaltyChargeFinancialTransaction),
            List(submitReturnRequest)
          )

          r1.map(_.map(_.isRecentlyAmended).headOption) shouldBe Right(Some(true))
        }

        "there is a recently amended return and it does not match one of the returns" in {
          val submitReturnRequest =
            sample[SubmitReturnRequest].copy(amendReturnData =
              Some(
                sample[AmendReturnData].copy(
                  originalReturn = sample[CompleteReturnWithSummary].copy(
                    summary = sample[ReturnSummary].copy(submissionId = "invalid-form-bundle-id")
                  )
                )
              )
            )

          val r1 = transformer.toReturnSummaryList(
            List(validDesReturnSummary1),
            List(mainCharge1FinancialTransaction, mainCharge2FinancialTransaction, penaltyChargeFinancialTransaction),
            List(submitReturnRequest)
          )

          r1.map(_.map(_.isRecentlyAmended).headOption) shouldBe Right(Some(false))
        }

        "finding the submission id" in {
          result.map(_.map(_.submissionId)) shouldBe Right(
            List(validDesReturnSummary1.submissionId, validDesReturnSummary2.submissionId)
          )
        }

        "finding the submission data" in {
          result.map(_.map(_.submissionDate)) shouldBe Right(
            List(validDesReturnSummary1.submissionDate, validDesReturnSummary2.submissionDate)
          )
        }

        "finding the completion date" in {
          result.map(_.map(_.completionDate)) shouldBe Right(
            List(validDesReturnSummary1.completionDate, validDesReturnSummary2.completionDate)
          )

        }

        "finding the tax year" in {
          result.map(_.map(_.taxYear)) shouldBe Right(
            List(validDesReturnSummary1.taxYear, validDesReturnSummary2.taxYear)
          )
        }

        "finding the main return charge amount" in {
          result.map(_.map(s => s.mainReturnChargeAmount -> s.mainReturnChargeReference)) shouldBe Right(
            List(
              AmountInPence.fromPounds(mainCharge1FinancialTransaction.originalAmount) -> Some(
                mainCharge1FinancialTransaction.chargeReference
              ),
              AmountInPence.fromPounds(mainCharge2FinancialTransaction.originalAmount) -> Some(
                mainCharge2FinancialTransaction.chargeReference
              )
            )
          )
        }

        "finding the property address" in {
          result.map(_.map(_.propertyAddress)) shouldBe Right(
            List(
              ukAddress1.copy(
                postcode = Postcode(ukAddress1.postcode.stripAllSpaces)
              ),
              ukAddress2.copy(
                postcode = Postcode(ukAddress2.postcode.stripAllSpaces)
              )
            )
          )
        }

        "finding the charge types" in {
          result.map(_.flatMap(_.charges.map(_.chargeType))) shouldBe Right(
            List(ChargeType.UkResidentReturn, ChargeType.UkResidentReturn, ChargeType.LateFilingPenalty)
          )
        }

        "finding charge references" in {
          result.map(_.flatMap(_.charges.map(_.chargeReference))) shouldBe Right(
            List(mainCharge1.chargeReference, mainCharge2.chargeReference, penaltyCharge.chargeReference)
          )
        }

        "finding the charge amounts" in {
          result.map(_.flatMap(_.charges.map(_.amount))) shouldBe Right(
            List(
              mainCharge1FinancialTransaction.originalAmount,
              mainCharge2FinancialTransaction.originalAmount,
              penaltyChargeFinancialTransaction.originalAmount
            ).map(AmountInPence.fromPounds)
          )
        }

        "finding the payments" in {
          result.map(_.flatMap(_.charges.map(_.payments))) shouldBe Right(
            List(
              List.empty, // first return summary has no payments
              List(
                Payment(
                  AmountInPence.fromPounds(mainCharge2PaymentAmount),
                  Some(PaymentMethod.CHAPS),
                  mainCharge2PaymentDate,
                  Some(ClearingReason.fromString(mainCharge2ClearingReason))
                )
              ),
              List.empty // penalty has no payments
            )
          )
        }

        "finding the main charge return amount when a nil return is found" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary1.copy(
                totalCGTLiability = BigDecimal("0"),
                charges = None
              )
            ),
            List(sample[DesFinancialTransaction].copy(items = None)),
            List.empty
          )

          result match {
            case Right(r :: Nil) =>
              r.mainReturnChargeAmount    shouldBe AmountInPence.zero
              r.mainReturnChargeReference shouldBe None
              r.charges                   shouldBe List.empty
            case other           => fail(s"Expected one return summary but got $other")
          }

        }

        "finding a delta charge if one exists" in {
          val deltaChargeDueDate                    = mainCharge1.dueDate.plusYears(1L)
          val deltaCharge                           = mainCharge1.copy(dueDate = deltaChargeDueDate)
          val (mainChargeAmount, deltaChargeAmount) = BigDecimal(1) -> BigDecimal(2)
          val result                                = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary1.copy(
                charges = Some(
                  List(
                    mainCharge1,
                    deltaCharge
                  )
                )
              )
            ),
            List(
              sample[DesFinancialTransaction].copy(
                chargeReference = mainCharge1.chargeReference,
                items = Some(
                  List(
                    DesFinancialTransactionItem(Some(mainChargeAmount), None, None, None, Some(mainDueDate1))
                  )
                ),
                originalAmount = mainChargeAmount
              ),
              sample[DesFinancialTransaction].copy(
                chargeReference = mainCharge1.chargeReference,
                items = Some(
                  List(
                    DesFinancialTransactionItem(Some(deltaChargeAmount), None, None, None, Some(deltaChargeDueDate))
                  )
                ),
                originalAmount = deltaChargeAmount
              )
            ),
            List.empty
          )

          result match {
            case Right(r :: Nil) =>
              r.mainReturnChargeAmount    shouldBe AmountInPence.fromPounds(mainChargeAmount + deltaChargeAmount)
              r.mainReturnChargeReference shouldBe Some(mainCharge1.chargeReference)
              r.charges.toSet             shouldBe Set(
                Charge(
                  UkResidentReturn,
                  mainCharge1.chargeReference,
                  AmountInPence.fromPounds(mainChargeAmount),
                  mainDueDate1,
                  List.empty
                ),
                Charge(
                  DeltaCharge,
                  mainCharge1.chargeReference,
                  AmountInPence.fromPounds(deltaChargeAmount),
                  deltaChargeDueDate,
                  List.empty
                )
              )
            case other           => fail(s"Expected one return summary but got $other")
          }
        }
      }

      "accept non uk addresses" in {
        val nonUkAddress          = NonUkAddress("1 the Street", None, None, None, None, sample[Country])
        val dueDate               = LocalDate.ofEpochDay(1)
        val mainCharge            = DesCharge(
          "CGT PPD Return UK Resident",
          dueDate,
          "reference1"
        )
        val validDesReturnSummary =
          sample[DesReturnSummary].copy(
            propertyAddress = Address.toAddressDetails(nonUkAddress),
            charges = Some(List(mainCharge))
          )

        val mainChargeFinancialTransaction = sample[DesFinancialTransaction].copy(
          chargeReference = mainCharge.chargeReference,
          items = Some(
            List(
              DesFinancialTransactionItem(
                Some(BigDecimal(1)),
                None,
                None,
                None,
                Some(dueDate)
              )
            )
          )
        )

        val result = transformer.toReturnSummaryList(
          List(validDesReturnSummary),
          List(mainChargeFinancialTransaction),
          List.empty
        )

        result.map(_.map(_.propertyAddress)) shouldBe Right(
          List(nonUkAddress)
        )
      }

    }

  }

}
