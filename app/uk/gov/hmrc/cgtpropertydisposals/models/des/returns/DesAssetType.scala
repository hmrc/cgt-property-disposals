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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AssetType, CompleteSingleDisposalReturn}

sealed trait DesAssetType extends Product with Serializable

object DesAssetType {

  case object Residential extends DesAssetType

  case object NonResidential extends DesAssetType

  case object MixedUse extends DesAssetType

  case object IndirectDisposal extends DesAssetType

  def apply(completeReturn: CompleteSingleDisposalReturn): DesAssetType = completeReturn.triageAnswers.assetType match {
    case AssetType.Residential      => Residential
    case AssetType.NonResidential   => NonResidential
    case AssetType.MixedUse         => MixedUse
    case AssetType.IndirectDisposal => IndirectDisposal
  }

  implicit val format: Format[DesAssetType] =
    Format(
      { json: JsValue =>
        json match {
          case JsString("residential")       => JsSuccess(Residential)
          case JsString("non residential")   => JsSuccess(NonResidential)
          case JsString("mixed use")         => JsSuccess(MixedUse)
          case JsString("indirect disposal") => JsSuccess(IndirectDisposal)
          case JsString(other)               => JsError(s"Could not parse asset type: $other")
          case other                         => JsError(s"Expected string for asset type but got $other")
        }
      }, { assetType: DesAssetType =>
        assetType match {
          case Residential      => JsString("residential")
          case NonResidential   => JsString("non residential")
          case MixedUse         => JsString("mixed use")
          case IndirectDisposal => JsString("indirect disposal")

        }
      }
    )
}
