/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.Json

class TaxYearExchangedSpec extends AnyWordSpec with Matchers {

  "TaxYearExchanged" must {

    "new case class" when {

      "given case class object should match with an expected new json " in {

        val taxYearExchanged = TaxYearExchanged(year = 2021)

        Json.toJson(taxYearExchanged) shouldBe Json.parse(
          """
            |    {"year": 2021}
            |""".stripMargin
        )

      }
    }

    "old case class" when {

      "given old json object should match with an expected new case class " in {

        val oldTaxTearExchanged = """
            | {"TaxYear2021":{}}
            |""".stripMargin

        Json.parse(oldTaxTearExchanged).as[TaxYearExchanged] shouldBe TaxYearExchanged(year = 2021)

      }
    }

  }
}
