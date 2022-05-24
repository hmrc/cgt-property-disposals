/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsPath, Json, OFormat, Reads}
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country

sealed trait MultipleDisposalsTriageAnswers

object MultipleDisposalsTriageAnswers {

  final case class CompleteMultipleDisposalsTriageAnswers(
    individualUserType: Option[IndividualUserType],
    numberOfProperties: Int,
    countryOfResidence: Country,
    assetTypes: List[AssetType],
    taxYearExchanged: TaxYearExchanged,
    taxYear: TaxYear,
    alreadySentSelfAssessment: Option[Boolean],
    completionDate: CompletionDate
  ) extends MultipleDisposalsTriageAnswers

  val answersFormat: OFormat[MultipleDisposalsTriageAnswers] = {
    val read: Reads[MultipleDisposalsTriageAnswers] = (
      (JsPath \ "individualUserType").readNullable[IndividualUserType] and
        (JsPath \ "numberOfProperties").read[Int] and
        (JsPath \ "countryOfResidence").read[Country] and
        (JsPath \ "assetTypes").read[List[AssetType]] and
        (JsPath \ "taxYearExchanged").read[TaxYearExchanged] and
        (JsPath \ "taxYear").read[TaxYear] and
        (JsPath \ "alreadySentSelfAssessment").readNullable[Boolean] and
        (JsPath \ "completionDate").read[CompletionDate]
    )(MultipleDisposalsTriageAnswers.apply, _)

    OFormat[MultipleDisposalsTriageAnswers](read, Json.writes[MultipleDisposalsTriageAnswers])
  }

  //@SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  //implicit val format: OFormat[MultipleDisposalsTriageAnswers] = derived.oformat()

}
