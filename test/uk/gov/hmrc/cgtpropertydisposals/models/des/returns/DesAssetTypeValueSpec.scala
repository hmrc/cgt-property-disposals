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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AssetType
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteSingleDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers

class DesAssetTypeValueSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "DesAssetTypeValue" must {

    "have a method which can convert asset types in single disposals and convert them back" in {
      forAll {
        assetType: AssetType =>
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(
              assetType = assetType
            )
          )

          DesAssetTypeValue(completeReturn).toAssetTypes() shouldBe Right(List(assetType))
      }
    }

    "have a method which can convert asset types in multiple disposals and convert them back" in {
      forAll { assetTypes: List[AssetType] =>
        whenever(assetTypes.nonEmpty) {
          val completeReturn = sample[CompleteMultipleDisposalsReturn].copy(
            triageAnswers = sample[CompleteMultipleDisposalsTriageAnswers].copy(
              assetTypes = assetTypes
            )
          )

          DesAssetTypeValue(completeReturn).toAssetTypes().map(_.toSet) shouldBe Right(assetTypes.toSet)
        }
      }

    }

    "have a method which can convert asset types in single mixed use disposals and convert them back" in {
      forAll {
        assetType: AssetType =>
          val completeReturn = sample[CompleteSingleMixedUseDisposalReturn].copy(
            triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(
              assetType = assetType
            )
          )

          DesAssetTypeValue(completeReturn).toAssetTypes() shouldBe Right(List(assetType))
      }

    }

    "be able to return an error when it encounters an unknown asset type" in {
      DesAssetTypeValue("abc").toAssetTypes() shouldBe a[Left[_, _]]

    }

  }

}
