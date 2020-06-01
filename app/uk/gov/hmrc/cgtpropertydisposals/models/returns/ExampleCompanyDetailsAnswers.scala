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
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait ExampleCompanyDetailsAnswers extends Product with Serializable

object ExampleCompanyDetailsAnswers {

  final case class IncompleteExampleCompanyDetailsAnswers(
    address: Option[Address],
    disposalPrice: Option[AmountInPence],
    acquisitionPrice: Option[AmountInPence]
  ) extends ExampleCompanyDetailsAnswers

  final case class CompleteExampleCompanyDetailsAnswers(
    address: Address,
    disposalPrice: AmountInPence,
    acquisitionPrice: AmountInPence
  ) extends ExampleCompanyDetailsAnswers

  @silent
  implicit val format: OFormat[ExampleCompanyDetailsAnswers] = derived.oformat()

}
