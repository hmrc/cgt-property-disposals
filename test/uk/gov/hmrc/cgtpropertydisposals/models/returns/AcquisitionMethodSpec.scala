/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesAcquisitionType
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionMethod._

class AcquisitionMethodSpec extends AnyWordSpec with Matchers {

  "AcquisitionMethod" must {

    "have a method" which {

      "converts from DesAcquisitionType" in {
        AcquisitionMethod(DesAcquisitionType.Bought)       shouldBe Bought
        AcquisitionMethod(DesAcquisitionType.Inherited)    shouldBe Inherited
        AcquisitionMethod(DesAcquisitionType.Gifted)       shouldBe Gifted
        AcquisitionMethod(DesAcquisitionType.Other("abc")) shouldBe Other("abc")

      }

    }

  }

}
