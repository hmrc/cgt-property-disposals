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

package uk.gov.hmrc.cgtpropertydisposals.service

import java.util.UUID

import cats.data.EitherT
import org.scalacheck.ScalacheckShapeless._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.RegisterWithoutIdConnector
import uk.gov.hmrc.cgtpropertydisposals.models.ids.SapNumber
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, RegistrationDetails, UUIDGenerator, sample}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RegisterWithoutIdServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector = mock[RegisterWithoutIdConnector]

  val mockUUIDGenerator = mock[UUIDGenerator]

  val service = new RegisterWithoutIdServiceImpl(mockConnector, mockUUIDGenerator)

  def mockGenerateUUID(uuid: UUID) =
    (mockUUIDGenerator.nextId: () => UUID).expects().returning(uuid)

  def mockRegisterWithoutId(expectedRegistrationDetails: RegistrationDetails, expectedReferenceId: UUID)(
    response: Either[Error, HttpResponse]) =
    (mockConnector
      .registerWithoutId(_: RegistrationDetails, _: UUID)(_: HeaderCarrier))
      .expects(expectedRegistrationDetails, expectedReferenceId, *)
      .returning(EitherT(Future.successful(response)))

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
          }

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          inSequence {
            mockGenerateUUID(referenceId)
            mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(200)))
          }

          await(service.registerWithoutId(registrationDetails).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          inSequence {
            mockGenerateUUID(referenceId)
            mockRegisterWithoutId(registrationDetails, referenceId)(Right(HttpResponse(200, Some(JsNumber(1)))))
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
        }

        await(service.registerWithoutId(registrationDetails).value) shouldBe Right(SapNumber(sapNumber))
      }
    }
  }
}
