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

import julienrf.json.derived
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait DisposalDetailsAnswers extends Product with Serializable

object DisposalDetailsAnswers {

  final case class IncompleteDisposalDetailsAnswers(
    shareOfProperty: Option[ShareOfProperty],
    disposalPrice: Option[AmountInPence],
    disposalFees: Option[AmountInPence]
  ) extends DisposalDetailsAnswers

  final case class CompleteDisposalDetailsAnswers(
    shareOfProperty: ShareOfProperty,
    disposalPrice: AmountInPence,
    disposalFees: AmountInPence
  ) extends DisposalDetailsAnswers

  implicit val format: OFormat[DisposalDetailsAnswers] = derived.oformat[DisposalDetailsAnswers]()

}
