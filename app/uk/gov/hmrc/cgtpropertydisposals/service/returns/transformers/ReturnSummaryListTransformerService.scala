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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import cats.instances.list._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, DesFinancialTransaction, DesFinancialTransactionItem}
import uk.gov.hmrc.cgtpropertydisposals.models.finance._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReturnSummary
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, Validation, invalid}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.{DesCharge, DesReturnSummary}

@ImplementedBy(classOf[ReturnSummaryListTransformerServiceImpl])
trait ReturnSummaryListTransformerService {

  def toReturnSummaryList(
    returns: List[DesReturnSummary],
    financialData: List[DesFinancialTransaction]
  ): Either[Error, List[ReturnSummary]]

}

@Singleton
class ReturnSummaryListTransformerServiceImpl extends ReturnSummaryListTransformerService {

  import ReturnSummaryListTransformerServiceImpl._

  def toReturnSummaryList(
    returns: List[DesReturnSummary],
    financialData: List[DesFinancialTransaction]
  ): Either[Error, List[ReturnSummary]] = {
    val chargeReferenceToFinancialData =
      financialData.map(t => t.chargeReference -> t).toMap

    val returnSummaries: List[Validation[ReturnSummary]] =
      returns.map { returnSummary =>
        validateReturnSummary(returnSummary, chargeReferenceToFinancialData)
      }

    returnSummaries
      .sequence[Validated[NonEmptyList[String], ?], ReturnSummary]
      .toEither
      .leftMap(e => Error(s"Could not convert return summaries with financial data: [${e.toList.mkString("; ")}]"))
  }

  private def validateReturnSummary(
    returnSummary: DesReturnSummary,
    chargeReferenceToFinancialData: Map[String, DesFinancialTransaction]
  ): Validation[ReturnSummary] = {
    val chargesValidation: Validation[List[Charge]] = validateCharges(returnSummary, chargeReferenceToFinancialData)
    val addressValidation: Validation[UkAddress] = AddressDetails
      .fromDesAddressDetails(returnSummary.propertyAddress)(List.empty)
      .andThen {
        case a: UkAddress    => Valid(a)
        case _: NonUkAddress => invalid("Expected uk address but found non-uk address")
      }

    val mainChargeValidation: Validation[Charge] = chargesValidation.andThen { charges =>
      charges.filter(c =>
        c.chargeType === ChargeType.UkResidentReturn || c.chargeType === ChargeType.NonUkResidentReturn
      ) match {
        case mainReturnCharge :: Nil => Valid(mainReturnCharge)
        case Nil =>
          invalid(
            s"Could not find charge with main return type. Found charge types ${charges.map(_.chargeType).toString}"
          )
        case _ => invalid(s"Found more than one charge with a main return type")
      }
    }

    (chargesValidation, addressValidation, mainChargeValidation).mapN {
      case (charges, address, mainReturnCharge) =>
        ReturnSummary(
          returnSummary.submissionId,
          returnSummary.submissionDate,
          returnSummary.completionDate,
          returnSummary.lastUpdatedDate,
          returnSummary.taxYear,
          mainReturnCharge.amount,
          address,
          charges
        )
    }
  }

  private def validateCharges(
    returnSummary: DesReturnSummary,
    chargeReferenceToFinancialData: Map[String, DesFinancialTransaction]
  ): Validation[List[Charge]] = {
    val charges: List[Validation[Charge]] = returnSummary.charges
      .getOrElse(List.empty[DesCharge])
      .map { returnSummaryCharge =>
        val chargeTypeValidation: Validation[ChargeType] =
          ChargeType.fromString(returnSummaryCharge.chargeDescription).toValidatedNel
        val financialDataValidation: Validation[(DesFinancialTransaction, List[Payment])] =
          Either
            .fromOption(
              chargeReferenceToFinancialData.get(returnSummaryCharge.chargeReference),
              s"Could not find financial data for charge with charge reference ${returnSummaryCharge.chargeReference}"
            )
            .toValidatedNel
            .andThen { t =>
              validatePayments(t).map(t -> _)
            }

        (chargeTypeValidation, financialDataValidation).mapN {
          case (chargeType, (financialData, payments)) =>
            Charge(
              chargeType,
              returnSummaryCharge.chargeReference,
              AmountInPence.fromPounds(financialData.originalAmount),
              returnSummaryCharge.dueDate,
              payments
            )
        }
      }
    charges.sequence[Validated[NonEmptyList[String], ?], Charge]
  }

  private def validatePayments(
    transaction: DesFinancialTransaction
  ): Validation[List[Payment]] = {
    val (paymentErrors, payments) = transaction.items
      .getOrElse(List.empty)
      .map {
        case DesFinancialTransactionItem(Some(paymentAmount), Some(paymentMethod), Some(clearingDate)) =>
          PaymentMethod
            .fromString(paymentMethod)
            .map(method => Some(Payment(AmountInPence.fromPounds(paymentAmount), method, clearingDate)))

        case DesFinancialTransactionItem(None, None, None) =>
          Right(None)

        case other =>
          Left(
            s"Could not find all required fields for a payment: (paymentAmount = ${other.paymentAmount}, " +
              s"paymentMethod = ${other.paymentMethod}, clearingDate = ${other.clearingDate}  "
          )
      }
      .partitionWith(identity)

    paymentErrors match {
      case h :: t => Invalid(NonEmptyList(h, t))
      case Nil    => Valid(payments.collect { case Some(p) => p })
    }
  }

}

object ReturnSummaryListTransformerServiceImpl {

  implicit class ListOps[A](private val l: List[A]) extends AnyVal {
    def partitionWith[B, C](f: A => Either[B, C]): (List[B], List[C]) =
      l.foldLeft(List.empty[B] -> List.empty[C]) {
        case (acc, curr) =>
          f(curr).fold(
            b => (b :: acc._1) -> acc._2,
            c => acc._1        -> (c :: acc._2)
          )
      }
  }
}
