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

import cats.instances.string._
import cats.syntax.eq._

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.cgtpropertydisposals.models.ListUtils.ListOps
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AssetType, CompleteReturn}

final case class DesAssetTypeValue(value: String)

object DesAssetTypeValue {

  private object Values {
    val residential: String      = "res"
    val nonResidential: String   = "nonres"
    val mixedUse: String         = "mix"
    val indirectDisposal: String = "shares"
  }

  def apply(c: CompleteReturn): DesAssetTypeValue = {
    val assetTypes       = c
      .fold(
        _.triageAnswers.assetTypes,
        s => List(s.triageAnswers.assetType),
        s => List(s.triageAnswers.assetType),
        _.triageAnswers.assetTypes,
        s => List(s.triageAnswers.assetType)
      )
      .distinct
    val assetTypeStrings = assetTypes.map { a =>
      a match {
        case AssetType.Residential      => Values.residential
        case AssetType.NonResidential   => Values.nonResidential
        case AssetType.MixedUse         => Values.mixedUse
        case AssetType.IndirectDisposal => Values.indirectDisposal
      }
    }

    DesAssetTypeValue(assetTypeStrings.mkString(" "))
  }

  implicit class DesAssetTypeValueOps(private val a: DesAssetTypeValue) extends AnyVal {

    def toAssetTypes(): Either[String, List[AssetType]] = {
      val result                 = a.value.split(' ').toList.map {
        case Values.residential      => Right(AssetType.Residential)
        case Values.nonResidential   => Right(AssetType.NonResidential)
        case Values.mixedUse         => Right(AssetType.MixedUse)
        case Values.indirectDisposal => Right(AssetType.IndirectDisposal)
        case other                   => Left(other)
      }
      val (unknowns, assetTypes) = result.partitionWith(identity)
      if (unknowns.nonEmpty)
        Left(s"Could not parse asset types: [${unknowns.mkString(", ")}]")
      else
        Right(assetTypes)
    }

    def isIndirectDisposal(): Boolean = a.value === Values.indirectDisposal

  }

  implicit val format: Format[DesAssetTypeValue] =
    implicitly[Format[String]].inmap(DesAssetTypeValue(_), _.value)

}
