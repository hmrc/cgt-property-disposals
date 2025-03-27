/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.controllers.dms

import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.models.dms.DmsSubmissionResult

class DmsCallbackControllerSpec extends ControllerSpec {
  class Setup {
    protected val controller: DmsCallbackController                     =
      new DmsCallbackController(Helpers.stubMessagesControllerComponents())
    protected implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  }

  "DmsCallbackController" when {
    "handling dms callback" must {
      "return 202 Ok" in new Setup {
        private val dmsSubmissionResult = DmsSubmissionResult(
          "test id",
          "test status"
        )
        await(controller.callback()(request.withBody(dmsSubmissionResult))) shouldBe Ok
      }
    }
  }
}
