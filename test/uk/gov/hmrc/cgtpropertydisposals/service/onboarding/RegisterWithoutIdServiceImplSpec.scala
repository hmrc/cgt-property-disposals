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

package uk.gov.hmrc.cgtpropertydisposals.service.onboarding

import java.util.UUID

import cats.data.EitherT
import org.scalacheck.ScalacheckShapeless._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.RegisterWithoutIdConnector
import uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding.routes
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.SapNumber
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, UUIDGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RegisterWithoutIdServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector = mock[RegisterWithoutIdConnector]

  val mockAuditService = mock[AuditService]

  val mockUUIDGenerator = mock[UUIDGenerator]

  val service =
    new RegisterWithoutIdServiceImpl(mockConnector, mockUUIDGenerator, mockAuditService, MockMetrics.metrics)

  def mockGenerateUUID(uuid: UUID) =
    (mockUUIDGenerator.nextId: () => UUID).expects().returning(uuid)

  def mockRegisterWithoutId(expectedRegistrationDetails: RegistrationDetails, expectedReferenceId: UUID)(
    response: Either[Error, HttpResponse]
  ) =
    (mockConnector
      .registerWithoutId(_: RegistrationDetails, _: UUID)(_: HeaderCarrier))
      .expects(expectedRegistrationDetails, expectedReferenceId, *)
      .returning(EitherT(Future.successful(response)))

  def mockAuditRegistrationResponse(httpStatus: Int, httpBody: String, path: String) =
    (mockAuditService
      .sendRegistrationResponse(_: Int, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(httpStatus, *, path, *, *)
      .returning(())

  "RegisterWithoutIdServiceImpl" when {

    "handling requests to register without id" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val registrationDetails        = sample[RegistrationDetails]
      val referenceId                = UUID.randomUUID()

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          inSequence {
            mockGenerateUUID(referenceId)
            mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(500)))
            mockAuditRegistrationResponse(500, "", routes.SubscriptionController.registerWithoutId().url)
          }

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          inSequence {
            mockGenerateUUID(referenceId)
            mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(200)))
            mockAuditRegistrationResponse(200, "", routes.SubscriptionController.registerWithoutId().url)
          }

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          inSequence {
            mockGenerateUUID(referenceId)
            mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(200, Some(JsNumber(1)))))
            mockAuditRegistrationResponse(200, "1", routes.SubscriptionController.registerWithoutId().url)
          }

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }
      }
      "return the sap number if the call comes back with a 200 status and the JSON body can be parsed" in {
        val sapNumber = "number"
        val jsonBody = Json.parse(
          s"""
             |{
             |  "sapNumber" : "$sapNumber"
             |}
             |""".stripMargin
        )

        inSequence {
          mockGenerateUUID(referenceId)
          mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(200, Some(jsonBody))))
          mockAuditRegistrationResponse(
            200,
            jsonBody.toString,
            routes.SubscriptionController.registerWithoutId().url
          )

        }

        await(service.registerWithoutId(registrationDetails).value) shouldBe Right(SapNumber(sapNumber))
      }
    }
  }
}
