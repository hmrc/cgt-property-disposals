/*
 * Copyright 2021 HM Revenue & Customs
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

import julienrf.json.derived
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country

sealed trait MultipleDisposalsTriageAnswers

object MultipleDisposalsTriageAnswers {

  final case class IncompleteMultipleDisposalsTriageAnswers(
    individualUserType: Option[IndividualUserType],
    numberOfProperties: Option[Int],
    wasAUKResident: Option[Boolean],
    countryOfResidence: Option[Country],
    wereAllPropertiesResidential: Option[Boolean],
    assetTypes: Option[List[AssetType]],
    taxYearExchanged: Option[TaxYearExchanged],
    taxYear: Option[TaxYear],
    alreadySentSelfAssessment: Option[Boolean],
    completionDate: Option[CompletionDate]
  ) extends MultipleDisposalsTriageAnswers

  final case class CompleteMultipleDisposalsTriageAnswers(
    individualUserType: Option[IndividualUserType],
    numberOfProperties: Int,
    countryOfResidence: Country,
    assetTypes: List[AssetType],
    taxYearExchanged: TaxYearExchanged,
    taxYear: TaxYear,
    alreadySentSelfAssessment: Boolean,
    completionDate: CompletionDate
  ) extends MultipleDisposalsTriageAnswers
  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  implicit val format: OFormat[MultipleDisposalsTriageAnswers] = derived.oformat()
}
