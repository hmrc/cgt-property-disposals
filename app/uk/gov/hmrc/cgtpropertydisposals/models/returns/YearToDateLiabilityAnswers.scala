/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.{CompleteCalculatedYTDAnswers, IncompleteCalculatedYTDAnswers}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.{CompleteNonCalculatedYTDAnswers, IncompleteNonCalculatedYTDAnswers}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanUpload

sealed trait YearToDateLiabilityAnswers extends Product with Serializable

object YearToDateLiabilityAnswers {

  sealed trait CalculatedYTDAnswers extends YearToDateLiabilityAnswers

  sealed trait NonCalculatedYTDAnswers extends YearToDateLiabilityAnswers

  object NonCalculatedYTDAnswers {

    final case class IncompleteNonCalculatedYTDAnswers(
      taxableGainOrLoss: Option[AmountInPence],
      hasEstimatedDetails: Option[Boolean],
      taxDue: Option[AmountInPence],
      mandatoryEvidence: Option[MandatoryEvidence],
      expiredEvidence: Option[MandatoryEvidence],
      pendingUpscanUpload: Option[UpscanUpload],
      yearToDateLiability: Option[AmountInPence],
      checkForRepayment: Option[Boolean],
      estimatedIncome: Option[AmountInPence],
      personalAllowance: Option[AmountInPence]
    ) extends NonCalculatedYTDAnswers

    object IncompleteNonCalculatedYTDAnswers {
      val empty: IncompleteNonCalculatedYTDAnswers =
        IncompleteNonCalculatedYTDAnswers(None, None, None, None, None, None, None, None, None, None)
    }

    final case class CompleteNonCalculatedYTDAnswers(
      taxableGainOrLoss: AmountInPence,
      hasEstimatedDetails: Boolean,
      taxDue: AmountInPence,
      mandatoryEvidence: Option[MandatoryEvidence],
      yearToDateLiability: Option[AmountInPence],
      checkForRepayment: Option[Boolean],
      estimatedIncome: Option[AmountInPence],
      personalAllowance: Option[AmountInPence]
    ) extends NonCalculatedYTDAnswers

  }

  object CalculatedYTDAnswers {
    final case class IncompleteCalculatedYTDAnswers(
      estimatedIncome: Option[AmountInPence],
      personalAllowance: Option[AmountInPence],
      hasEstimatedDetails: Option[Boolean],
      calculatedTaxDue: Option[CalculatedTaxDue],
      taxDue: Option[AmountInPence],
      mandatoryEvidence: Option[MandatoryEvidence],
      expiredEvidence: Option[MandatoryEvidence],
      pendingUpscanUpload: Option[UpscanUpload]
    ) extends CalculatedYTDAnswers

    object IncompleteCalculatedYTDAnswers {
      val empty: IncompleteCalculatedYTDAnswers =
        IncompleteCalculatedYTDAnswers(None, None, None, None, None, None, None, None)
    }

    final case class CompleteCalculatedYTDAnswers(
      estimatedIncome: AmountInPence,
      personalAllowance: Option[AmountInPence],
      hasEstimatedDetails: Boolean,
      calculatedTaxDue: CalculatedTaxDue,
      taxDue: AmountInPence,
      mandatoryEvidence: Option[MandatoryEvidence]
    ) extends CalculatedYTDAnswers

  }

  implicit val completeCalculatedFormat: OFormat[CompleteCalculatedYTDAnswers]     =
    Json.format[CompleteCalculatedYTDAnswers]
  implicit val inCompleteCalculatedFormat: OFormat[IncompleteCalculatedYTDAnswers] =
    Json.format[IncompleteCalculatedYTDAnswers]
  implicit val calculatedFormat: OFormat[CalculatedYTDAnswers]                     = Json.format[CalculatedYTDAnswers]

  implicit val completeNonCalculatedFormat: OFormat[CompleteNonCalculatedYTDAnswers]     =
    Json.format[CompleteNonCalculatedYTDAnswers]
  implicit val inCompleteNonCalculatedFormat: OFormat[IncompleteNonCalculatedYTDAnswers] =
    Json.format[IncompleteNonCalculatedYTDAnswers]
  implicit val nonCalculatedFormat: OFormat[NonCalculatedYTDAnswers]                     = Json.format[NonCalculatedYTDAnswers]

  implicit val format: OFormat[YearToDateLiabilityAnswers] = new OFormat[YearToDateLiabilityAnswers] {
    override def reads(json: JsValue): JsResult[YearToDateLiabilityAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("CompleteCalculatedYTDAnswers", value)      => value.validate[CompleteCalculatedYTDAnswers]
          case ("IncompleteCalculatedYTDAnswers", value)    => value.validate[IncompleteCalculatedYTDAnswers]
          case ("CompleteNonCalculatedYTDAnswers", value)   => value.validate[CompleteNonCalculatedYTDAnswers]
          case ("IncompleteNonCalculatedYTDAnswers", value) => value.validate[IncompleteNonCalculatedYTDAnswers]
          case (other, _)                                   => JsError(s"Unknown YearToDateLiabilityAnswers subtype: $other")
        }
      case _                                    =>
        JsError("Expected wrapper object with one YearToDateLiabilityAnswers subtype key")
    }

    override def writes(o: YearToDateLiabilityAnswers): JsObject = o match {
      case c: CompleteCalculatedYTDAnswers      => Json.obj("CompleteCalculatedYTDAnswers" -> Json.toJson(c))
      case i: IncompleteCalculatedYTDAnswers    => Json.obj("IncompleteCalculatedYTDAnswers" -> Json.toJson(i))
      case c: CompleteNonCalculatedYTDAnswers   => Json.obj("CompleteNonCalculatedYTDAnswers" -> Json.toJson(c))
      case i: IncompleteNonCalculatedYTDAnswers => Json.obj("IncompleteNonCalculatedYTDAnswers" -> Json.toJson(i))
    }
  }

  implicit val eitherYTDFormat: Format[Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]] =
    new Format[Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]] {
      def writes(e: Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]): JsValue = e match {
        case Left(nonCalc) => Json.toJson(nonCalc)
        case Right(calc)   => Json.toJson(calc)
      }

      def reads(json: JsValue): JsResult[Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]] =
        json match {
          case JsObject(fields) if fields.size == 1 =>
            fields.head match {
              case ("CompleteNonCalculatedYTDAnswers", value) =>
                value.validate[CompleteNonCalculatedYTDAnswers].map(Left(_))
              case ("CompleteCalculatedYTDAnswers", value)    =>
                value.validate[CompleteCalculatedYTDAnswers].map(Right(_))
              case (other, _)                                 => JsError(s"Unknown YearToDateLiabilityAnswers subtype: $other")
            }
          case _                                    =>
            JsError("Expected wrapper object with one YearToDateLiabilityAnswers subtype key")
        }
    }

}
