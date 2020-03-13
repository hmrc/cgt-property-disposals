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
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesAcquisitionType._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AcquisitionMethod, CompleteReturn}

class DesAcquisitionTypeSpec extends WordSpec with Matchers {

  "DesAcquisitionType" must {

    "have a format instance" which {

      "can write JSON properly" in {
        def test(c: DesAcquisitionType, expectedJson: JsValue) = Json.toJson(c) shouldBe expectedJson

        test(Bought, JsString("bought"))
        test(Inherited, JsString("inherited"))
        test(Gifted, JsString("gifted"))
        test(Other("abc"), JsString("abc"))
      }

      "can read JSON properly" in {
        JsString("bought").validate[DesAcquisitionType]    shouldBe JsSuccess(Bought)
        JsString("inherited").validate[DesAcquisitionType] shouldBe JsSuccess(Inherited)
        JsString("gifted").validate[DesAcquisitionType]    shouldBe JsSuccess(Gifted)
        JsString("def").validate[DesAcquisitionType]       shouldBe JsSuccess(Other("def"))
        JsBoolean(true).validate[DesAcquisitionType]       shouldBe a[JsError]
      }

    }

    "have a method which converts from a complete return" in {

      def test(acquisitionMethod: AcquisitionMethod, expectedDesAcquisitionType: DesAcquisitionType) = {
        val completeReturn = sample[CompleteReturn].copy(
          acquisitionDetails = sample[CompleteAcquisitionDetailsAnswers].copy(
            acquisitionMethod = acquisitionMethod
          )
        )

        DesAcquisitionType(completeReturn) shouldBe expectedDesAcquisitionType
      }

      test(AcquisitionMethod.Bought, Bought)
      test(AcquisitionMethod.Inherited, Inherited)
      test(AcquisitionMethod.Gifted, Gifted)
      test(AcquisitionMethod.Other("abc"), Other("abc"))
    }

  }

}
