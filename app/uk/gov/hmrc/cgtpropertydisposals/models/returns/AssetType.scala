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

import julienrf.json.derived
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesAssetType

sealed trait AssetType extends Product with Serializable

object AssetType {

  case object Residential extends AssetType

  case object NonResidential extends AssetType

  case object IndirectDisposal extends AssetType

  case object MixedUse extends AssetType

  def apply(desAssetType: DesAssetType): AssetType = desAssetType match {
    case DesAssetType.Residential      => Residential
    case DesAssetType.NonResidential   => NonResidential
    case DesAssetType.MixedUse         => MixedUse
    case DesAssetType.IndirectDisposal => IndirectDisposal
  }

  implicit val format: OFormat[AssetType] = derived.oformat()

}
