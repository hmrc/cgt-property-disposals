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

package uk.gov.hmrc.cgtpropertydisposals.service.onboarding

import cats.data.EitherT
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doNothing, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.{JsNumber, JsValue, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.RegisterWithoutIdConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.OnboardingGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.ids.SapNumber
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.RegistrationResponseEvent
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, UUIDGenerator}
import uk.gov.hmrc.cgtpropertydisposals.service.audit.AuditService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RegisterWithoutIdServiceImplSpec extends AnyWordSpec with Matchers {

  private val mockConnector = mock[RegisterWithoutIdConnector]

  private val mockAuditService = mock[AuditService]

  private val mockUUIDGenerator = mock[UUIDGenerator]

  val service =
    new RegisterWithoutIdServiceImpl(mockConnector, mockUUIDGenerator, mockAuditService, MockMetrics.metrics)

  private def mockGenerateUUID(uuid: UUID) =
    when(mockUUIDGenerator.nextId()).thenReturn(uuid)

  private def mockRegisterWithoutId(expectedRegistrationDetails: RegistrationDetails, expectedReferenceId: UUID)(
    response: Either[Error, HttpResponse]
  ) =
    when(
      mockConnector
        .registerWithoutId(ArgumentMatchers.eq(expectedRegistrationDetails), ArgumentMatchers.eq(expectedReferenceId))(
          using any()
        )
    ).thenReturn(EitherT(Future.successful(response)))

  private def mockAuditRegistrationResponse(httpStatus: Int, responseBody: Option[JsValue]): Unit =
    doNothing()
      .when(mockAuditService)
      .sendEvent(
        ArgumentMatchers.eq("registrationResponse"),
        ArgumentMatchers.eq(
          RegistrationResponseEvent(
            httpStatus,
            responseBody.getOrElse(Json.parse("""{ "body" : "could not parse body as JSON: " }"""))
          )
        ),
        ArgumentMatchers.eq("registration-response")
      )(using
        any(),
        any(),
        any()
      )

  private val noJsonInBody = ""

  "RegisterWithoutIdServiceImpl" when {
    "handling requests to register without id" must {
      implicit val hc: HeaderCarrier   = HeaderCarrier()
      implicit val request: Request[?] = FakeRequest()
      val registrationDetails          = sample[RegistrationDetails]
      val referenceId                  = UUID.randomUUID()

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          mockGenerateUUID(referenceId)
          mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(500, noJsonInBody)))
          mockAuditRegistrationResponse(500, None)

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockGenerateUUID(referenceId)
          mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(200, noJsonInBody)))
          mockAuditRegistrationResponse(200, None)

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockGenerateUUID(referenceId)
          mockRegisterWithoutId(registrationDetails, referenceId)(
            Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]]))
          )
          mockAuditRegistrationResponse(200, Some(JsNumber(1)))

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }
      }

      "return the sap number if the call comes back with a 200 status and the JSON body can be parsed" in {
        val sapNumber = "number"
        val jsonBody  = Json.parse(
          s"""
             |{
             |  "sapNumber" : "$sapNumber"
             |}
             |""".stripMargin
        )

        mockGenerateUUID(referenceId)
        mockRegisterWithoutId(registrationDetails, referenceId)(
          Right(HttpResponse(200, jsonBody, Map.empty[String, Seq[String]]))
        )
        mockAuditRegistrationResponse(200, Some(jsonBody))

        await(service.registerWithoutId(registrationDetails).value) shouldBe Right(SapNumber(sapNumber))
      }
    }
  }
}
