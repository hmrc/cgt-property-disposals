/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.LocalDate

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{DesFinancialTransaction, DesFinancialTransactionItem}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.{AmountInPence, ChargeType, Payment, PaymentMethod}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.{DesCharge, DesReturnSummary}

class ReturnSummaryListTransformerServiceImplSpec extends WordSpec with Matchers {

  "ReturnSummaryListTransformerServiceImpl" when {

    val transformer = new ReturnSummaryListTransformerServiceImpl

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
          items = Some(List(DesFinancialTransactionItem(None, None, None)))
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
            List(validDesFinancialTransaction)
          )

          result.isLeft shouldBe true
        }

        "no financial data can be found for a charge in the return summary list" in {
          val result = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary
            ),
            List.empty
          )

          result.isLeft shouldBe true
        }

        "some but not all payment information is found" in {
          val date          = LocalDate.ofEpochDay(0L)
          val amount        = BigDecimal(1)
          val paymentMethod = "CHAPS"

          List(
            (None, Some(paymentMethod), Some(date)),
            (Some(amount), None, Some(date)),
            (Some(amount), Some(paymentMethod), None),
            (None, None, Some(date)),
            (Some(amount), None, None),
            (None, Some(paymentMethod), None)
          ).foreach {
            case (amount, paymentMethod, date) =>
              withClue(s"For (amount, paymentMethod, date) = ($amount, $paymentMethod, $date): ") {
                val result = transformer.toReturnSummaryList(
                  List(
                    validDesReturnSummary
                  ),
                  List(
                    validDesFinancialTransaction.copy(
                      items = Some(
                        List(DesFinancialTransactionItem(amount, paymentMethod, date))
                      )
                    )
                  )
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
            List(validDesFinancialTransaction)
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
            List(validDesFinancialTransaction)
          )

          result.isLeft shouldBe true

        }

        "a return summary is found with more than one UkResidentReturn or NonUkResidentReturn charge type" in {
          val otherChargeReference = s"${validDesCharge.chargeReference}-copy"
          val result               = transformer.toReturnSummaryList(
            List(
              validDesReturnSummary.copy(
                charges = Some(
                  List(validDesCharge, validDesCharge.copy(chargeReference = otherChargeReference))
                )
              )
            ),
            List(
              validDesFinancialTransaction,
              validDesFinancialTransaction.copy(chargeReference = otherChargeReference)
            )
          )

          result.isLeft shouldBe true
        }

      }

      "transform the data correctly" when {
        val (ukAddress1, ukAddress2) = sample[UkAddress] -> sample[UkAddress]

        val (mainCharge1, mainCharge2, penaltyCharge) =
          (
            DesCharge(
              "CGT PPD Return UK Resident",
              LocalDate.ofEpochDay(1),
              "reference1"
            ),
            DesCharge(
              "CGT PPD Return UK Resident",
              LocalDate.ofEpochDay(2),
              "reference2"
            ),
            DesCharge(
              "CGT PPD Late Filing Penalty",
              LocalDate.ofEpochDay(3),
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
          items = None
        )

        val (mainCharge2PaymentAmount, mainCharge2PaymentMethod, mainCharge2PaymentDate) =
          (BigDecimal(10), "CHAPS", LocalDate.ofEpochDay(5L))

        val mainCharge2FinancialTransaction = sample[DesFinancialTransaction].copy(
          chargeReference = mainCharge2.chargeReference,
          items = Some(
            List(
              DesFinancialTransactionItem(
                Some(mainCharge2PaymentAmount),
                Some(mainCharge2PaymentMethod),
                Some(mainCharge2PaymentDate)
              )
            )
          )
        )

        val penaltyChargeFinancialTransaction = sample[DesFinancialTransaction].copy(
          chargeReference = penaltyCharge.chargeReference,
          items = Some(List(DesFinancialTransactionItem(None, None, None)))
        )

        val result = transformer.toReturnSummaryList(
          List(validDesReturnSummary1, validDesReturnSummary2),
          List(mainCharge1FinancialTransaction, mainCharge2FinancialTransaction, penaltyChargeFinancialTransaction)
        )

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
          result.map(_.map(_.mainReturnChargeAmount)) shouldBe Right(
            List(
              mainCharge1FinancialTransaction.originalAmount,
              mainCharge2FinancialTransaction.originalAmount
            ).map(AmountInPence.fromPounds)
          )
        }

        "finding the property address" in {
          result.map(_.map(_.propertyAddress)) shouldBe Right(
            List(ukAddress1, ukAddress2)
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
                  PaymentMethod.CHAPS,
                  mainCharge2PaymentDate
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
            List(sample[DesFinancialTransaction].copy(items = None))
          )

          result match {
            case Right(r :: Nil) =>
              r.mainReturnChargeAmount shouldBe AmountInPence.zero
              r.charges                shouldBe List.empty
            case other           => fail(s"Expected one return summary but got $other")
          }

        }

      }

    }

  }

}
