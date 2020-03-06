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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsError, JsNumber, JsString, JsSuccess, JsValue, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesAssetType._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AssetType, CompleteReturn}

class DesAssetTypeSpec extends WordSpec with Matchers {

  "DesAssetType" must {

    "have a format instance" which {

      "can write JSON properly" in {
        def test(c: DesAssetType, expectedJson: JsValue) = Json.toJson(c) shouldBe expectedJson

        test(Residential, JsString("residential"))
        test(NonResidential, JsString("non residential"))
        test(MixedUse, JsString("mixed use"))
        test(IndirectDisposal, JsString("indirect disposal"))
      }

      "can read JSON properly" in {
        JsString("residential").validate[DesAssetType]       shouldBe JsSuccess(Residential)
        JsString("non residential").validate[DesAssetType]   shouldBe JsSuccess(NonResidential)
        JsString("mixed use").validate[DesAssetType]         shouldBe JsSuccess(MixedUse)
        JsString("indirect disposal").validate[DesAssetType] shouldBe JsSuccess(IndirectDisposal)
        JsString("???").validate[DesAssetType]               shouldBe a[JsError]
        JsNumber(1).validate[DesAssetType]                   shouldBe a[JsError]

      }

    }

    "have a method which converts from a complete return" in {

      def test(assetType: AssetType, expectedDesAssetType: DesAssetType) = {
        val completeReturn = sample[CompleteReturn].copy(triageAnswers =
          sample[CompleteSingleDisposalTriageAnswers].copy(
            assetType = assetType
          )
        )

        DesAssetType(completeReturn) shouldBe expectedDesAssetType
      }

      test(AssetType.Residential, Residential)
      test(AssetType.NonResidential, NonResidential)
      test(AssetType.MixedUse, MixedUse)
      test(AssetType.IndirectDisposal, IndirectDisposal)

    }

  }

}
