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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.models.BprRequest.{Individual, Organisation}
import uk.gov.hmrc.cgtpropertydisposals.models.{BprRequest, DateOfBirth, NINO, Name, SAUTR}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class BusinessPartnerRecordConnectorImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  val config = Configuration(
    ConfigFactory.parseString(
      s"""
      |microservice {
      |  services {
      |      business-partner-record {
      |      protocol = http
      |      host     = host
      |      port     = 123
      |    }
      |  }
      |}
      |
      |des {
      |  bearer-token = $desBearerToken
      |  environment  = $desEnvironment
      |}
      |""".stripMargin
    )
  )

  val connector =
    new BusinessPartnerRecordConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  "BusinessPartnerRecordConnectorImpl" when {

    "handling request to get the business partner records" when {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val expectedHeaders            = Map("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

      "handling individuals with NINOs" must {
        val nino        = NINO("AB123456C")
        val name        = Name("forename", "surname")
        val dateOfBirth = DateOfBirth(LocalDate.parse("2001-09-20", DateTimeFormatter.ISO_LOCAL_DATE))
        val bprRequest  = BprRequest(Right(Individual(nino, name, dateOfBirth)))

        val expectedBody = Json.parse(s"""
                                            | {
                                            |   "regime" : "HMRC-CGT-PD",
                                            |   "requiresNameMatch" : false,
                                            |   "isAnIndividual" : true,
                                            |   "individual" : {
                                            |     "firstName" : "forename",
                                            |     "lastName" : "surname",
                                            |     "dateOfBirth" : "2001-09-20"
                                            |    }
                                            | }
                                            |""".stripMargin)

        "do a post http call and return the result" in {
          List(
            HttpResponse(200),
            HttpResponse(200, Some(JsString("hi"))),
            HttpResponse(500)
          ).foreach { httpResponse =>
            withClue(s"For http response [${httpResponse.toString}]") {
              mockPost(s"http://host:123/registration/individual/nino/${nino.value}", expectedHeaders, expectedBody)(
                Some(httpResponse)
              )

              await(connector.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(httpResponse)
            }
          }
        }

        "return an error when the future fails" in {
          mockPost(s"http://host:123/registration/individual/nino/${nino.value}", expectedHeaders, expectedBody)(None)

          await(connector.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }
      }

      "handling organisations with SAUTRs" must {
        val sautr      = SAUTR("sautr")
        val bprRequest = BprRequest(Left(Organisation(sautr)))

        val expectedBody = Json.parse(s"""
                                            | {
                                            |   "regime" : "HMRC-CGT-PD",
                                            |   "requiresNameMatch" : false,
                                            |   "isAnIndividual" : false
                                            | }
                                            |""".stripMargin)

        "do a post http call and return the result" in {
          List(
            HttpResponse(200),
            HttpResponse(200, Some(JsString("hi"))),
            HttpResponse(500)
          ).foreach { httpResponse =>
            withClue(s"For http response [${httpResponse.toString}]") {
              mockPost(s"http://host:123/registration/individual/sautr/${sautr.value}", expectedHeaders, expectedBody)(
                Some(httpResponse)
              )

              await(connector.getBusinessPartnerRecord(bprRequest).value) shouldBe Right(httpResponse)
            }
          }
        }

        "return an error when the future fails" in {
          mockPost(s"http://host:123/registration/individual/sautr/${sautr.value}", expectedHeaders, expectedBody)(None)

          await(connector.getBusinessPartnerRecord(bprRequest).value).isLeft shouldBe true
        }
      }

    }

  }

}
