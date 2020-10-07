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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import cats.instances.bigDecimal._
import cats.instances.list._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.ListUtils.ListOps
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, DesFinancialTransaction, DesFinancialTransactionItem}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.ChargeType.{DeltaCharge, NonUkResidentReturn, UkResidentReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.finance._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{ReturnSummary, SubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, Validation, invalid}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.{DesCharge, DesReturnSummary}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging

@ImplementedBy(classOf[ReturnSummaryListTransformerServiceImpl])
trait ReturnSummaryListTransformerService {

  def toReturnSummaryList(
    returns: List[DesReturnSummary],
    financialData: List[DesFinancialTransaction],
    recentlyAmendedReturns: List[SubmitReturnRequest]
  ): Either[Error, List[ReturnSummary]]

}

@Singleton
class ReturnSummaryListTransformerServiceImpl extends ReturnSummaryListTransformerService with Logging {

  def toReturnSummaryList(
    returns: List[DesReturnSummary],
    financialData: List[DesFinancialTransaction],
    recentlyAmendedReturns: List[SubmitReturnRequest]
  ): Either[Error, List[ReturnSummary]] = {
    val chargeReferenceToFinancialData: Map[String, List[DesFinancialTransaction]] =
      financialData.groupBy(_.chargeReference)

    val returnSummaries: List[Validation[ReturnSummary]] =
      returns.map { returnSummary =>
        validateReturnSummary(
          returnSummary,
          chargeReferenceToFinancialData,
          recentlyAmendedReturns.exists(recentlyAmendedReturn =>
            recentlyAmendedReturn.amendReturnData
              .map(_.originalReturn.summary.submissionId)
              .contains(returnSummary.submissionId)
          )
        )
      }

    returnSummaries
      .sequence[Validated[NonEmptyList[String], *], ReturnSummary]
      .toEither
      .leftMap(e => Error(s"Could not convert return summaries with financial data: [${e.toList.mkString("; ")}]"))
  }

  private def validateReturnSummary(
    returnSummary: DesReturnSummary,
    chargeReferenceToFinancialData: Map[String, List[DesFinancialTransaction]],
    isRecentlyAmendedReturn: Boolean
  ): Validation[ReturnSummary] = {
    val chargesValidation: Validation[List[Charge]] =
      validateCharges(returnSummary, chargeReferenceToFinancialData)

    val addressValidation: Validation[Address] =
      AddressDetails.fromDesAddressDetails(returnSummary.propertyAddress, allowNonIsoCountryCodes = false)

    val mainReturnChargeAmountValidation: Validation[AmountInPence] = chargesValidation.andThen { charges =>
      if (returnSummary.totalCGTLiability === BigDecimal("0") && charges.isEmpty)
        Valid(AmountInPence.zero)
      else
        charges.filter(c =>
          c.chargeType === ChargeType.UkResidentReturn || c.chargeType === ChargeType.NonUkResidentReturn
        ) match {
          case mainReturnCharge :: Nil =>
            val extraAmount = charges.find(_.chargeType === DeltaCharge).map(_.amount).getOrElse(AmountInPence.zero)
            Valid(mainReturnCharge.amount ++ extraAmount)
          case Nil                     =>
            invalid(
              s"Could not find charge with main return type. Found charge types ${charges.map(_.chargeType).toString}"
            )
          case _                       => invalid(s"Found more than one charge with a main return type")
        }
    }

    (chargesValidation, addressValidation, mainReturnChargeAmountValidation).mapN {
      case (charges, address, mainReturnChargeAmount) =>
        ReturnSummary(
          returnSummary.submissionId,
          returnSummary.submissionDate,
          returnSummary.completionDate,
          returnSummary.lastUpdatedDate,
          returnSummary.taxYear,
          mainReturnChargeAmount,
          address,
          charges,
          isRecentlyAmendedReturn
        )
    }
  }

  // get a List of DesCharges with unique charge references. The charges in the return summary can
  // include charges with the same charge reference if payments have been made on a charge
  private def uniqueCharges(returnSummary: DesReturnSummary): List[DesCharge] = {
    val chargeReferenceToCharges: Map[String, List[DesCharge]] =
      returnSummary.charges.getOrElse(List.empty[DesCharge]).groupBy(_.chargeReference)

    chargeReferenceToCharges.values.map(_.headOption).collect { case Some(charge) => charge }.toList
  }

  private def validateCharges(
    returnSummary: DesReturnSummary,
    chargeReferenceToFinancialData: Map[String, List[DesFinancialTransaction]]
  ): Validation[List[Charge]] = {
    val charges: List[Validation[List[Charge]]] =
      uniqueCharges(returnSummary)
        .map { returnSummaryCharge =>
          val chargeTypeValidation: Validation[ChargeType]                                  =
            ChargeType.fromString(returnSummaryCharge.chargeDescription).toValidatedNel
          val financialDataValidation: Validation[(DesFinancialTransaction, List[Payment])] =
            Either
              .fromOption(
                chargeReferenceToFinancialData
                  .get(returnSummaryCharge.chargeReference)
                  .flatMap(
                    _.find(_.items.exists(_.exists(_.dueDate.contains(returnSummaryCharge.dueDate))))
                  ),
                s"Could not find financial data for charge with charge reference ${returnSummaryCharge.chargeReference}"
              )
              .toValidatedNel
              .andThen(t => validatePayments(t).map(t -> _))

          val secondaryChargeValidation                                                     = validateSecondaryCharge(
            returnSummaryCharge,
            chargeTypeValidation,
            financialDataValidation,
            chargeReferenceToFinancialData
          )

          (chargeTypeValidation, financialDataValidation, secondaryChargeValidation).mapN {
            case (chargeType, (financialData, payments), maybeSecondaryCharge) =>
              val charge          = Charge(
                chargeType,
                returnSummaryCharge.chargeReference,
                AmountInPence.fromPounds(financialData.originalAmount),
                returnSummaryCharge.dueDate,
                payments
              )
              val secondaryCharge = maybeSecondaryCharge.map { d =>
                Charge(
                  chargeType,
                  returnSummaryCharge.chargeReference,
                  AmountInPence.fromPounds(d._1.originalAmount),
                  d._3,
                  d._2
                )
              }

              secondaryCharge.fold(List(charge)) { secondaryCharge =>
                if (charge.dueDate.isBefore(secondaryCharge.dueDate))
                  List(charge, secondaryCharge.copy(chargeType = DeltaCharge))
                else
                  List(secondaryCharge, charge.copy(chargeType = DeltaCharge))
              }

          }
        }

    charges.sequence[Validated[NonEmptyList[String], *], List[Charge]].map(_.flatten)
  }

  // if a delta charge has been raised, the main charge and the delta charge won't appear in the
  // return summary together. Look for the delta/main charge if there is one if a delta/main charge
  // has already been found
  private def validateSecondaryCharge(
    returnSummaryCharge: DesCharge,
    chargeTypeValidation: Validation[ChargeType],
    financialDataValidation: Validation[(DesFinancialTransaction, List[Payment])],
    chargeReferenceToFinancialData: Map[String, List[DesFinancialTransaction]]
  ): Validation[Option[(DesFinancialTransaction, List[Payment], LocalDate)]] = {
    def getDueDate(transaction: DesFinancialTransaction): Option[LocalDate] =
      transaction.items.flatMap(_.collectFirst { case DesFinancialTransactionItem(_, _, _, _, Some(dueDate)) =>
        dueDate
      })

    chargeTypeValidation.andThen {
      case UkResidentReturn | NonUkResidentReturn =>
        chargeReferenceToFinancialData.get(returnSummaryCharge.chargeReference) match {
          case None                                      => invalid("Could not find transaction for main return charge type")
          case Some(Nil) | Some(_ :: Nil)                => Valid(None)
          case Some(transaction1 :: transaction2 :: Nil) =>
            financialDataValidation.andThen { case (foundTransaction, _) =>
              val secondaryChargeTransaction = if (foundTransaction === transaction1) transaction2 else transaction1

              Either
                .fromOption(
                  getDueDate(secondaryChargeTransaction),
                  s"Could not find due date for transaction $secondaryChargeTransaction"
                )
                .toValidatedNel
                .andThen { dueDate =>
                  validatePayments(secondaryChargeTransaction).map(payments =>
                    Some((secondaryChargeTransaction, payments, dueDate))
                  )
                }
            }

          case _ => invalid("Found more than two charges with main return charge type")
        }

      case _ => Valid(None)
    }
  }

  private def validatePayments(
    transaction: DesFinancialTransaction
  ): Validation[List[Payment]] = {
    val (paymentErrors, payments) = transaction.items
      .getOrElse(List.empty)
      .map {
        case DesFinancialTransactionItem(
              Some(amount),
              Some(paymentMethod),
              Some(clearingDate),
              Some(clearingReason),
              _
            ) =>
          PaymentMethod
            .fromString(paymentMethod) match {
            case Left(_)              =>
              Right(
                Some(
                  Payment(
                    AmountInPence.fromPounds(amount),
                    None,
                    clearingDate,
                    Some(ClearingReason.fromString(clearingReason))
                  )
                )
              )
            case Right(paymentMethod) =>
              Right(
                Some(
                  Payment(
                    AmountInPence.fromPounds(amount),
                    Some(paymentMethod),
                    clearingDate,
                    Some(ClearingReason.fromString(clearingReason))
                  )
                )
              )
          }
        case DesFinancialTransactionItem(
              Some(amount),
              None,
              Some(clearingDate),
              Some(clearingReason),
              _
            ) =>
          Right(
            Some(
              Payment(
                AmountInPence.fromPounds(amount),
                None,
                clearingDate,
                Some(ClearingReason.fromString(clearingReason))
              )
            )
          )

        case DesFinancialTransactionItem(
              Some(amount),
              Some(paymentMethod),
              Some(clearingDate),
              None,
              _
            ) =>
          PaymentMethod
            .fromString(paymentMethod)
            .map(paymentMethod =>
              Some(
                Payment(
                  AmountInPence.fromPounds(amount),
                  Some(paymentMethod),
                  clearingDate,
                  None
                )
              )
            )

        case DesFinancialTransactionItem(_, None, _, None, _) =>
          Right(None)

        case other =>
          Left(
            s"Could not find some of the required fields for a payment: (amount = ${other.amount}, " +
              s"paymentMethod = ${other.paymentMethod}, clearingDate = ${other.clearingDate} " +
              s"clearingReason = ${other.clearingReason} "
          )
      }
      .partitionWith(identity)

    paymentErrors match {
      case h :: t => Invalid(NonEmptyList(h, t))
      case Nil    => Valid(payments.collect { case Some(p) => p })
    }
  }

}
