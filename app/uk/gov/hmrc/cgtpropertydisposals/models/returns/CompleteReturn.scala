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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import com.github.ghik.silencer.silent
import julienrf.json.derived
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat.eitherFormat
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.UploadSupportingEvidenceAnswers.CompleteUploadSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

sealed trait CompleteReturn

object CompleteReturn {

  final case class CompleteMultipleDisposalsReturn(
    triageAnswers: CompleteMultipleDisposalsTriageAnswers,
    examplePropertyDetailsAnswers: CompleteExamplePropertyDetailsAnswers,
    exemptionAndLossesAnswers: CompleteExemptionAndLossesAnswers,
    yearToDateLiabilityAnswers: CompleteNonCalculatedYTDAnswers,
    uploadSupportingDocumentAnswers: CompleteUploadSupportingEvidenceAnswers
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
    uploadSupportingDocumentAnswers: CompleteUploadSupportingEvidenceAnswers
  ) extends CompleteReturn

  implicit class CompleteReturnOps(private val c: CompleteReturn) extends AnyVal {

    def fold[A](
      ifMultiple: CompleteMultipleDisposalsReturn => A,
      ifSingle: CompleteSingleDisposalReturn => A
    ): A = c match {
      case m: CompleteMultipleDisposalsReturn => ifMultiple(m)
      case s: CompleteSingleDisposalReturn    => ifSingle(s)
    }

  }

  @silent
  implicit val format: OFormat[CompleteReturn] = {
    implicit val singleDisposalTriageFormat: OFormat[CompleteSingleDisposalTriageAnswers]         = Json.format
    implicit val multipleDisposalsTriageFormat: OFormat[CompleteMultipleDisposalsTriageAnswers]   = Json.format
    implicit val ukAddressFormat: OFormat[UkAddress]                                              = Json.format
    implicit val examplePropertyDetailsFormat: OFormat[CompleteExamplePropertyDetailsAnswers]     = Json.format
    implicit val disposalDetailsFormat: OFormat[CompleteDisposalDetailsAnswers]                   = Json.format
    implicit val acquisitionDetailsFormat: OFormat[CompleteAcquisitionDetailsAnswers]             = Json.format
    implicit val reliefDetailsFormat: OFormat[CompleteReliefDetailsAnswers]                       = Json.format
    implicit val exemptionAndLossesFormat: OFormat[CompleteExemptionAndLossesAnswers]             = Json.format
    implicit val nonCalculatedYearToDateLiabilityFormat: OFormat[CompleteNonCalculatedYTDAnswers] = Json.format
    implicit val calculatedYearToDateLiabilityFormat: OFormat[CompleteCalculatedYTDAnswers]       = Json.format
    implicit val uploadSupportingDocuments: OFormat[CompleteUploadSupportingEvidenceAnswers]      = Json.format
    derived.oformat()
  }

}
