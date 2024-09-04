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

package stubs

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}

object AuthStub extends WireMockMethods {
  private val authoriseUri: String = "/auth/authorise"

  private val credentials: JsObject = Json.obj(
    "providerId"   -> "someProviderId",
    "providerType" -> "GovernmentGateway"
  )

  val enrolmentModel: Enrolment = Enrolment(
    key = "HMRC-TERS-ORG",
    identifiers = Seq(
      EnrolmentIdentifier(
        key = "SAUTR",
        value = "7000040245"
      )
    ),
    state = "Activated"
  )

  private val successfulAuthResponse: JsObject = Json.obj(
    "authorisedEnrolments" -> enrolmentModel.toJson,
    "affinityGroup"        -> "Individual",
    "optionalCredentials"  -> credentials
  )

  def authorised(): StubMapping =
    when(method = POST, uri = authoriseUri)
      .thenReturn(status = OK, body = successfulAuthResponse)

  def unauthorised(): StubMapping =
    when(method = POST, uri = authoriseUri)
      .thenReturn(status = UNAUTHORIZED, headers = Map("WWW-Authenticate" -> """MDTP detail="MissingBearerToken""""))
}
