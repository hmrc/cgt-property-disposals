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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsError, JsNull, JsString, JsSuccess, JsValue, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.SubmissionType.{Amend, New}

class SubmissionTypeSpec extends WordSpec with Matchers {

  "SubmissionType" must {

    "have a format instance" which {

      "writes JSON correctly" in {
        def test(s: SubmissionType, expectedJson: JsValue) = Json.toJson(s) shouldBe expectedJson

        test(New, JsString("New"))
        test(Amend, JsString("Amend"))
      }

      "reads JSON correctly" in {
        JsString("New").validate[SubmissionType]   shouldBe JsSuccess(New)
        JsString("Amend").validate[SubmissionType] shouldBe JsSuccess(Amend)
        JsString("abc").validate[SubmissionType]   shouldBe a[JsError]
        JsNull.validate[SubmissionType]            shouldBe a[JsError]

      }

    }

  }

}
