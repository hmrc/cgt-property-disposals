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

package uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.test.Helpers._
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{NINO, SAUTR, TRN}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.BusinessPartnerRecordRequest.{IndividualBusinessPartnerRecordRequest, TrustBusinessPartnerRecordRequest}
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

      val name = IndividualName("forename", "surname")

      def individualBprRequest(id: Either[SAUTR, NINO], requiresNameMatch: Boolean) =
        IndividualBusinessPartnerRecordRequest(id, if (requiresNameMatch) Some(name) else None)

      def expectedIndividualBody(requiresNameMatch: Boolean) = {
        val individualJsonString =
          if (requiresNameMatch)
            """,
            |  "individual" : {
            |     "firstName" : "forename",
            |     "lastName" : "surname"
            |    }
            |""".stripMargin
          else ""

        Json.parse(s"""
                      | {
                      |   "regime" : "CGT",
                      |   "requiresNameMatch" : $requiresNameMatch,
                      |   "isAnAgent" : false$individualJsonString
                      | }
                      |""".stripMargin)
      }

      "handling individuals with NINOs" must {
        val nino = NINO("AB123456C")

        "do a post http call and return the result" in {
          List(
            HttpResponse(200),
            HttpResponse(200, Some(JsString("hi"))),
            HttpResponse(500)
          ).foreach { httpResponse =>
            withClue(s"For http response [${httpResponse.toString}]") {
              mockPost(
                s"http://host:123/registration/individual/nino/${nino.value}",
                expectedHeaders,
                expectedIndividualBody(true)
              )(
                Some(httpResponse)
              )

              await(connector.getBusinessPartnerRecord(individualBprRequest(Right(nino), true)).value) shouldBe Right(
                httpResponse
              )
            }
          }
        }

        "return an error when the future fails" in {
          mockPost(
            s"http://host:123/registration/individual/nino/${nino.value}",
            expectedHeaders,
            expectedIndividualBody(false)
          )(None)

          await(connector.getBusinessPartnerRecord(individualBprRequest(Right(nino), false)).value).isLeft shouldBe true
        }

        "strip out spaces in NINOs" in {
          mockPost(
            s"http://host:123/registration/individual/nino/AA123456C",
            expectedHeaders,
            expectedIndividualBody(false)
          )(None)

          await(
            connector.getBusinessPartnerRecord(individualBprRequest(Right(NINO("  AA 123 456C")), false)).value
          ).isLeft shouldBe true

        }
      }

      "handling individuals with SA UTRs" must {
        val sautr = SAUTR("12345")

        "do a post http call and return the result" in {
          List(
            HttpResponse(200),
            HttpResponse(200, Some(JsString("hi"))),
            HttpResponse(500)
          ).foreach { httpResponse =>
            withClue(s"For http response [${httpResponse.toString}]") {
              mockPost(
                s"http://host:123/registration/individual/utr/${sautr.value}",
                expectedHeaders,
                expectedIndividualBody(true)
              )(
                Some(httpResponse)
              )

              await(connector.getBusinessPartnerRecord(individualBprRequest(Left(sautr), true)).value) shouldBe Right(
                httpResponse
              )
            }
          }
        }

        "return an error when the future fails" in {
          mockPost(
            s"http://host:123/registration/individual/utr/${sautr.value}",
            expectedHeaders,
            expectedIndividualBody(false)
          )(None)

          await(connector.getBusinessPartnerRecord(individualBprRequest(Left(sautr), false)).value).isLeft shouldBe true
        }

        "strip out spaces in SA UTR's" in {
          mockPost(
            s"http://host:123/registration/individual/utr/12345",
            expectedHeaders,
            expectedIndividualBody(false)
          )(None)

          await(
            connector.getBusinessPartnerRecord(individualBprRequest(Left(SAUTR(" 12 34 5 ")), false)).value
          ).isLeft shouldBe true

        }
      }

      "handling organisations" must {
        val trn                                                              = TRN("t r n ")
        val sautr                                                            = SAUTR(" sa utr ")
        def bprRequest(id: Either[TRN, SAUTR], nameMatch: Option[TrustName]) =
          TrustBusinessPartnerRecordRequest(id, nameMatch)

        def expectedBody(nameMatch: Option[TrustName]): JsValue = {
          val organisationJsonString =
            nameMatch.fold("") { trustName =>
              s""",
                 |  "organisation" : {
                 |     "organisationName" : "${trustName.value}",
                 |     "organisationType" : "Not Specified"
                 |    }
                 |""".stripMargin
            }

          Json.parse(s"""
                        | {
                        |   "regime" : "CGT",
                        |   "requiresNameMatch" : ${nameMatch.isDefined},
                        |   "isAnAgent" : false$organisationJsonString
                        | }
                        |""".stripMargin)
        }

        val idsWithExpectedUrlsList = List[(Either[TRN, SAUTR], String)](
          Left(trn)    -> s"http://host:123/registration/organisation/trn/trn",
          Right(sautr) -> s"http://host:123/registration/organisation/utr/sautr"
        )

        "do a post http call and return the result" in {
          for {
            httpResponse        <- List(
                                     HttpResponse(200),
                                     HttpResponse(200, Some(JsString("hi"))),
                                     HttpResponse(500)
                                   )
            idsWithExpectedUrls <- idsWithExpectedUrlsList
          } withClue(s"For http response [${httpResponse.toString}] and id ${idsWithExpectedUrls._1}") {
            mockPost(idsWithExpectedUrls._2, expectedHeaders, expectedBody(None))(
              Some(httpResponse)
            )

            await(connector.getBusinessPartnerRecord(bprRequest(idsWithExpectedUrls._1, None)).value) shouldBe Right(
              httpResponse
            )
          }
        }

        "pass in the trust name for name matching if one is passed in" in {
          val trustName    = TrustName("trust")
          val httpResponse = HttpResponse(200)

          idsWithExpectedUrlsList.foreach {
            case (id, expectedUrl) =>
              withClue(s"For id $id: ") {
                mockPost(expectedUrl, expectedHeaders, expectedBody(Some(trustName)))(
                  Some(httpResponse)
                )

                await(connector.getBusinessPartnerRecord(bprRequest(id, Some(trustName))).value) shouldBe Right(
                  httpResponse
                )
              }
          }

        }

        "return an error when the future fails" in {
          idsWithExpectedUrlsList.foreach {
            case (id, expectedUrl) =>
              withClue(s"For id $id: ") {
                mockPost(expectedUrl, expectedHeaders, expectedBody(None))(None)

                await(connector.getBusinessPartnerRecord(bprRequest(id, None)).value).isLeft shouldBe true
              }
          }
        }
      }
    }

  }

}
