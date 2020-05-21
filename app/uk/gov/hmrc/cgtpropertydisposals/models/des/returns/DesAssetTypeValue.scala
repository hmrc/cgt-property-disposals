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
import play.api.libs.functional.syntax._
import uk.gov.hmrc.cgtpropertydisposals.models.ListUtils.ListOps
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AssetType, CompleteReturn}

final case class DesAssetTypeValue(value: String)

object DesAssetTypeValue {

  def apply(c: CompleteReturn): DesAssetTypeValue = {
    val assetTypes       = c.fold(_.triageAnswers.assetTypes, s => List(s.triageAnswers.assetType)).distinct
    val assetTypeStrings = assetTypes.map { a =>
      a match {
        case AssetType.Residential      => "res"
        case AssetType.NonResidential   => "nonres"
        case AssetType.MixedUse         => "mix"
        case AssetType.IndirectDisposal => "shares"
      }
    }

    DesAssetTypeValue(assetTypeStrings.mkString(" "))
  }

  implicit class DesAssetTypeValueOps(private val a: DesAssetTypeValue) extends AnyVal {

    def toAssetTypes(): Either[String, List[AssetType]] = {
      val result                 = a.value.split(' ').toList.map {
        case "res"    => Right(AssetType.Residential)
        case "nonres" => Right(AssetType.NonResidential)
        case "mix"    => Right(AssetType.MixedUse)
        case "shares" => Right(AssetType.IndirectDisposal)
        case other    => Left(other)
      }
      val (unknowns, assetTypes) = result.partitionWith(identity)
      if (unknowns.nonEmpty)
        Left(s"Could not parse asset types: [${unknowns.mkString(", ")}]")
      else
        Right(assetTypes)
    }

  }

  implicit val format: Format[DesAssetTypeValue] =
    implicitly[Format[String]].inmap(DesAssetTypeValue(_), _.value)

}
