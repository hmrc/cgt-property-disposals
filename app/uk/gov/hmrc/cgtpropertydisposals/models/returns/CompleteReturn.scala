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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExampleCompanyDetailsAnswers.CompleteExampleCompanyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MixedUsePropertyDetailsAnswers.CompleteMixedUsePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.CompleteSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

sealed trait CompleteReturn

object CompleteReturn {

  final case class CompleteMultipleDisposalsReturn(
    triageAnswers: CompleteMultipleDisposalsTriageAnswers,
    examplePropertyDetailsAnswers: CompleteExamplePropertyDetailsAnswers,
    exemptionAndLossesAnswers: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteSingleDisposalReturn(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    propertyAddress: UkAddress,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    reliefDetails: CompleteReliefDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers],
    initialGainOrLoss: Option[AmountInPence],
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteSingleIndirectDisposalReturn(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    companyAddress: Address,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteMultipleIndirectDisposalReturn(
    triageAnswers: CompleteMultipleDisposalsTriageAnswers,
    exampleCompanyDetailsAnswers: CompleteExampleCompanyDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  final case class CompleteSingleMixedUseDisposalReturn(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    propertyDetailsAnswers: CompleteMixedUsePropertyDetailsAnswers,
    exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    supportingDocumentAnswers: CompleteSupportingEvidenceAnswers,
    representeeAnswers: Option[CompleteRepresenteeAnswers],
    gainOrLossAfterReliefs: Option[AmountInPence],
    hasAttachments: Boolean
  ) extends CompleteReturn

  implicit class CompleteReturnOps(private val c: CompleteReturn) extends AnyVal {

    def fold[A](
      ifMultiple: CompleteMultipleDisposalsReturn => A,
      ifSingle: CompleteSingleDisposalReturn => A,
      whenSingleIndirect: CompleteSingleIndirectDisposalReturn => A,
      whenMultipleIndirect: CompleteMultipleIndirectDisposalReturn => A,
      whenSingleMixedUse: CompleteSingleMixedUseDisposalReturn => A
    ): A =
      c match {
        case m: CompleteMultipleDisposalsReturn        => ifMultiple(m)
        case s: CompleteSingleDisposalReturn           => ifSingle(s)
        case s: CompleteSingleIndirectDisposalReturn   => whenSingleIndirect(s)
        case m: CompleteMultipleIndirectDisposalReturn => whenMultipleIndirect(m)
        case s: CompleteSingleMixedUseDisposalReturn   => whenSingleMixedUse(s)
      }

  }

  import DraftReturn.*

  implicit val singleDisposalTriageFormat: OFormat[CompleteSingleDisposalTriageAnswers]                       = Json.format
  implicit val multipleDisposalsTriageFormat: OFormat[CompleteMultipleDisposalsTriageAnswers]                 = Json.format
  implicit val ukAddressFormat: OFormat[UkAddress]                                                            = Json.format
  implicit val examplePropertyDetailsFormat: OFormat[CompleteExamplePropertyDetailsAnswers]                   = Json.format
  implicit val disposalDetailsFormat: OFormat[CompleteDisposalDetailsAnswers]                                 = Json.format
  implicit val acquisitionDetailsFormat: OFormat[CompleteAcquisitionDetailsAnswers]                           = Json.format
  implicit val reliefDetailsFormat: OFormat[CompleteReliefDetailsAnswers]                                     = Json.format
  implicit val exemptionAndLossesFormat: OFormat[CompleteExemptionAndLossesAnswers]                           = Json.format
  implicit val nonCalculatedYearToDateLiabilityFormat: OFormat[CompleteNonCalculatedYTDAnswers]               = Json.format
  implicit val calculatedYearToDateLiabilityFormat: OFormat[CompleteCalculatedYTDAnswers]                     = Json.format
  implicit val supportingDocumentsFormat: OFormat[CompleteSupportingEvidenceAnswers]                          = Json.format
  implicit val representeeAnswersFormat: OFormat[CompleteRepresenteeAnswers]                                  = Json.format
  implicit val exampleCompanyDetailsAnswersFormat: OFormat[CompleteExampleCompanyDetailsAnswers]              = Json.format
  implicit val mixedUsePropertyDetailsAnswersFormat: OFormat[CompleteMixedUsePropertyDetailsAnswers]          = Json.format
  implicit val completeMultipleDisposalsReturnFormat: OFormat[CompleteMultipleDisposalsReturn]                = Json.format
  implicit val completeMultipleIndirectDisposalsReturnFormat: OFormat[CompleteMultipleIndirectDisposalReturn] =
    Json.format
  implicit val completeSingleDisposalReturnFormat: OFormat[CompleteSingleDisposalReturn]                      = Json.format
  implicit val completeSingleMixedUseDisposalReturnFormat: OFormat[CompleteSingleMixedUseDisposalReturn]      = Json.format
  implicit val completeSingleIndirectDisposalReturnFormat: OFormat[CompleteSingleIndirectDisposalReturn]      = Json.format

  implicit val eitherFormat: Format[Either[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]] =
    EitherFormat.eitherFormat[CompleteNonCalculatedYTDAnswers, CompleteCalculatedYTDAnswers]

  implicit val format: OFormat[CompleteReturn] = new OFormat[CompleteReturn] {
    override def reads(json: play.api.libs.json.JsValue): play.api.libs.json.JsResult[CompleteReturn] =
      json match {
        case play.api.libs.json.JsObject(fields) if fields.size == 1 =>
          fields.head match {
            case ("CompleteMultipleDisposalsReturn", value)        =>
              value.validate[CompleteMultipleDisposalsReturn]
            case ("CompleteSingleDisposalReturn", value)           =>
              value.validate[CompleteSingleDisposalReturn]
            case ("CompleteSingleIndirectDisposalReturn", value)   =>
              value.validate[CompleteSingleIndirectDisposalReturn]
            case ("CompleteMultipleIndirectDisposalReturn", value) =>
              value.validate[CompleteMultipleIndirectDisposalReturn]
            case ("CompleteSingleMixedUseDisposalReturn", value)   =>
              value.validate[CompleteSingleMixedUseDisposalReturn]
            case (other, _)                                        =>
              play.api.libs.json.JsError(s"Unrecognized CompleteReturn type: $other")
          }
        case _                                                       =>
          play.api.libs.json.JsError("Expected a CompleteReturn wrapper object with a single entry")
      }

    override def writes(c: CompleteReturn): play.api.libs.json.JsObject = c match {
      case m: CompleteMultipleDisposalsReturn        =>
        play.api.libs.json.Json.obj("CompleteMultipleDisposalsReturn" -> play.api.libs.json.Json.toJson(m))
      case s: CompleteSingleDisposalReturn           =>
        play.api.libs.json.Json.obj("CompleteSingleDisposalReturn" -> play.api.libs.json.Json.toJson(s))
      case s: CompleteSingleIndirectDisposalReturn   =>
        play.api.libs.json.Json.obj("CompleteSingleIndirectDisposalReturn" -> play.api.libs.json.Json.toJson(s))
      case m: CompleteMultipleIndirectDisposalReturn =>
        play.api.libs.json.Json.obj("CompleteMultipleIndirectDisposalReturn" -> play.api.libs.json.Json.toJson(m))
      case s: CompleteSingleMixedUseDisposalReturn   =>
        play.api.libs.json.Json.obj("CompleteSingleMixedUseDisposalReturn" -> play.api.libs.json.Json.toJson(s))
    }
  }
}
