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
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesDisposalType._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalMethod

class DesDisposalTypeSpec extends AnyWordSpec with Matchers {

  "DesAcquisitionType" must {

    "have a format instance" which {

      "can write JSON properly" in {
        def test(c: DesDisposalType, expectedJson: JsValue) = Json.toJson(c) shouldBe expectedJson

        test(Sold, JsString("sold"))
        test(Gifted, JsString("gifted"))
        test(Other, JsString("other"))
      }

      "can read JSON properly" in {
        JsString("sold").validate[DesDisposalType]   shouldBe JsSuccess(Sold)
        JsString("gifted").validate[DesDisposalType] shouldBe JsSuccess(Gifted)
        JsString("other").validate[DesDisposalType]  shouldBe JsSuccess(Other)
        JsString("???").validate[DesDisposalType]    shouldBe a[JsError]
        JsNumber(1).validate[DesDisposalType]        shouldBe a[JsError]
      }

    }

    "have a method which converts from a complete return" in {
      DesDisposalType(DisposalMethod.Sold)   shouldBe Sold
      DesDisposalType(DisposalMethod.Gifted) shouldBe Gifted
    }

  }

}
