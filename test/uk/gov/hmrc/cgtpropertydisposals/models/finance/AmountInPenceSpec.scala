/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.finance

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*

class AmountInPenceSpec extends AnyWordSpec with Matchers {

  "AmountInPence JSON format" should {

    "succeed when value is equal to 5e10 (50,000,000,000)" in {
      val json = JsNumber(5e1.toLong)
      json.validate[AmountInPence] shouldBe JsSuccess(AmountInPence(5e1.toLong))
    }

    "fail validation when value is greater than 5e10" in {
      val invalidAmount = 6e10.toLong + 1L
      val jsonPayload   = JsNumber(invalidAmount)
      val result        = jsonPayload.validate[AmountInPence]
      result match {
        case JsError(errors) =>
          val messages = errors.flatMap(_._2).map(_.message)
          messages should contain("Amount greater than allowed")

        case JsSuccess(value, _) =>
          fail(s"Invalid Amount $value")
      }

    }

  }
}
