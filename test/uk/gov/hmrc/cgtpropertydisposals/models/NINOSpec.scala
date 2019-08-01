/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models

import org.scalatest.{Matchers, WordSpec}

class NINOSpec extends WordSpec with Matchers {

  "NINO" must {

    "have a PathBindable instance" in {
      import uk.gov.hmrc.cgtpropertydisposals.models.NINO.binder

      binder.bind("key", "value") shouldBe Right(NINO("value"))
      binder.unbind("key", NINO("value")) shouldBe "value"
    }

  }

}
