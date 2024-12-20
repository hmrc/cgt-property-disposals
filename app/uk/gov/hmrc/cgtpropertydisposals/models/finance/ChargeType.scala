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

package uk.gov.hmrc.cgtpropertydisposals.models.finance

import cats.Eq
import julienrf.json.derived
import play.api.libs.json.OFormat

sealed trait ChargeType extends Product with Serializable

object ChargeType {

  final case object UkResidentReturn extends ChargeType

  final case object NonUkResidentReturn extends ChargeType

  final case object DeltaCharge extends ChargeType

  final case object Interest extends ChargeType

  final case object LateFilingPenalty extends ChargeType

  final case object SixMonthLateFilingPenalty extends ChargeType

  final case object TwelveMonthLateFilingPenalty extends ChargeType

  final case object LatePaymentPenalty extends ChargeType

  final case object SixMonthLatePaymentPenalty extends ChargeType

  final case object TwelveMonthLatePaymentPenalty extends ChargeType

  final case object PenaltyInterest extends ChargeType

  def fromString(s: String): Either[String, ChargeType] =
    s match {
      case "CGT PPD Return UK Resident"     => Right(UkResidentReturn)
      case "CGT PPD Return Non UK Resident" => Right(NonUkResidentReturn)
      case "CGT PPD Interest"               => Right(Interest)
      case "CGT PPD Late Filing Penalty"    => Right(LateFilingPenalty)
      case "CGT PPD 6 Mth LFP"              => Right(SixMonthLateFilingPenalty)
      case "CGT PPD 12 Mth LFP"             => Right(TwelveMonthLateFilingPenalty)
      case "CGT PPD Late Payment Penalty"   => Right(LatePaymentPenalty)
      case "CGT PPD 6 Mth LPP"              => Right(SixMonthLatePaymentPenalty)
      case "CGT PPD 12 Mth LPP"             => Right(TwelveMonthLatePaymentPenalty)
      case "CGT PPD Penalty Interest"       => Right(PenaltyInterest)
      case other                            => Left(s"Could not parse charge type: $other")
    }

  implicit val eq: Eq[ChargeType] = Eq.fromUniversalEquals

  implicit val format: OFormat[ChargeType] = derived.oformat()

}
