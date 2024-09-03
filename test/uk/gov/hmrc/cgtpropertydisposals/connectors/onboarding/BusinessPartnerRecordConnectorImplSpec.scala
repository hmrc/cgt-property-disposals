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

package uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding

import com.typesafe.config.ConfigFactory
import org.mockito.IdiomaticMockito
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{NINO, SAUTR, TRN}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.BusinessPartnerRecordRequest.{IndividualBusinessPartnerRecordRequest, TrustBusinessPartnerRecordRequest}
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class BusinessPartnerRecordConnectorImplSpec
    extends AnyWordSpec
    with Matchers
    with IdiomaticMockito
    with WireMockSupport
    with WireMockMethods
    with GuiceOneAppPerSuite
    with EitherValues {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      business-partner-record {
         |      protocol = http
         |      host     = $wireMockHost
         |      port     = $wireMockPort
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

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  val connector: BusinessPartnerRecordConnector = app.injector.instanceOf[BusinessPartnerRecordConnector]

  private val emptyJsonBody = "{}"

  "BusinessPartnerRecordConnectorImpl" when {
    "handling request to get the business partner records" when {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

      val name = IndividualName("forename", "surname")

      val ggCredId = "ggCredId"

      def individualBprRequest(id: Either[SAUTR, NINO], requiresNameMatch: Boolean) =
        IndividualBusinessPartnerRecordRequest(
          id,
          if (requiresNameMatch) Some(name) else None,
          ggCredId,
          createNewEnrolmentIfMissing = true
        )

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
            HttpResponse(200, emptyJsonBody),
            HttpResponse(200, JsString("hi"), Map.empty[String, Seq[String]]),
            HttpResponse(500, emptyJsonBody)
          ).foreach { httpResponse =>
            withClue(s"For http response [${httpResponse.toString}]") {
              when(
                POST,
                s"/registration/individual/nino/${nino.value}",
                headers = expectedHeaders.toMap,
                body = Some(expectedIndividualBody(true).toString())
              ).thenReturn(httpResponse.status, httpResponse.body)

              val response = await(
                connector.getBusinessPartnerRecord(individualBprRequest(Right(nino), requiresNameMatch = true)).value
              ).value
              response.status shouldBe httpResponse.status
              response.body   shouldBe httpResponse.body
            }
          }
        }

        "return an error when the future fails" in {
          wireMockServer.stop()
          when(
            POST,
            s"/registration/individual/nino/${nino.value}",
            headers = expectedHeaders.toMap,
            body = Some(expectedIndividualBody(true).toString())
          )

          await(
            connector.getBusinessPartnerRecord(individualBprRequest(Right(nino), requiresNameMatch = false)).value
          ).isLeft shouldBe true
          wireMockServer.start()
        }

        "strip out spaces in NINOs" in {
          wireMockServer.stop()
          when(
            POST,
            "/registration/individual/nino/AA123456C",
            headers = expectedHeaders.toMap,
            body = Some(expectedIndividualBody(true).toString())
          )

          await(
            connector
              .getBusinessPartnerRecord(individualBprRequest(Right(NINO("  AA 123 456C")), requiresNameMatch = false))
              .value
          ).isLeft shouldBe true
          wireMockServer.start()
        }
      }

      "handling individuals with SA UTRs" must {
        val sautr = SAUTR("12345")

        "do a post http call and return the result" in {
          List(
            HttpResponse(200, emptyJsonBody),
            HttpResponse(200, JsString("hi"), Map.empty[String, Seq[String]]),
            HttpResponse(500, emptyJsonBody)
          ).foreach { httpResponse =>
            withClue(s"For http response [${httpResponse.toString}]") {
              when(
                POST,
                s"/registration/individual/utr/${sautr.value}",
                headers = expectedHeaders.toMap,
                body = Some(expectedIndividualBody(true).toString())
              ).thenReturn(httpResponse.status, httpResponse.body)

              val response = await(
                connector.getBusinessPartnerRecord(individualBprRequest(Left(sautr), requiresNameMatch = true)).value
              ).value
              response.status shouldBe httpResponse.status
              response.body   shouldBe httpResponse.body
            }
          }
        }

        "return an error when the future fails" in {
          wireMockServer.stop()
          when(
            POST,
            s"/registration/individual/utr/${sautr.value}",
            headers = expectedHeaders.toMap,
            body = Some(expectedIndividualBody(false).toString())
          )

          await(
            connector.getBusinessPartnerRecord(individualBprRequest(Left(sautr), requiresNameMatch = false)).value
          ).isLeft shouldBe true

          wireMockServer.start()
        }

        "strip out spaces in SA UTR's" in {
          wireMockServer.stop()
          when(
            POST,
            "/registration/individual/utr/12345",
            headers = expectedHeaders.toMap,
            body = Some(expectedIndividualBody(false).toString())
          )

          await(
            connector
              .getBusinessPartnerRecord(individualBprRequest(Left(SAUTR(" 12 34 5 ")), requiresNameMatch = false))
              .value
          ).isLeft shouldBe true
          wireMockServer.start()
        }
      }

      "handling organisations" must {
        val trn   = TRN("t r n ")
        val sautr = SAUTR(" sa utr ")

        def bprRequest(id: Either[TRN, SAUTR], nameMatch: Option[TrustName]) =
          TrustBusinessPartnerRecordRequest(id, nameMatch, ggCredId, createNewEnrolmentIfMissing = false)

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
          Left(trn)    -> "/registration/organisation/trn/trn",
          Right(sautr) -> "/registration/organisation/utr/sautr"
        )

        "do a post http call and return the result" in {
          for {
            httpResponse        <- List(
                                     HttpResponse(200, emptyJsonBody),
                                     HttpResponse(200, JsString("hi"), Map.empty[String, Seq[String]]),
                                     HttpResponse(500, emptyJsonBody)
                                   )
            idsWithExpectedUrls <- idsWithExpectedUrlsList
          } withClue(s"For http response [${httpResponse.toString}] and id ${idsWithExpectedUrls._1}") {
            when(
              POST,
              idsWithExpectedUrls._2,
              headers = expectedHeaders.toMap,
              body = Some(expectedBody(None).toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response =
              await(connector.getBusinessPartnerRecord(bprRequest(idsWithExpectedUrls._1, None)).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }

        "pass in the trust name for name matching if one is passed in" in {
          val trustName    = TrustName("trust")
          val httpResponse = HttpResponse(200, emptyJsonBody)

          idsWithExpectedUrlsList.foreach { case (id, expectedUrl) =>
            withClue(s"For id $id: ") {
              when(
                POST,
                expectedUrl,
                headers = expectedHeaders.toMap,
                body = Some(expectedBody(Some(trustName)).toString())
              ).thenReturn(httpResponse.status, httpResponse.body)

              val response =
                await(connector.getBusinessPartnerRecord(bprRequest(id, Some(trustName))).value).value
              response.status shouldBe httpResponse.status
              response.body   shouldBe httpResponse.body
            }
          }
        }

        "return an error when the future fails" in {
          idsWithExpectedUrlsList.foreach { case (id, expectedUrl) =>
            wireMockServer.stop()
            withClue(s"For id $id: ") {
              when(POST, expectedUrl, headers = expectedHeaders.toMap, body = Some(expectedBody(None).toString()))
              await(connector.getBusinessPartnerRecord(bprRequest(id, None)).value).isLeft shouldBe true
            }
            wireMockServer.start()
          }
        }
      }
    }
  }
}
