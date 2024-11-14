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

package uk.gov.hmrc.cgtpropertydisposals.connectors.dms

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import com.typesafe.config.ConfigFactory
import org.apache.pekko.util.ByteString
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.{AUTHORIZATION, await, defaultAwaitTimeout}
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{dmsMetadataGen, sample}
import uk.gov.hmrc.cgtpropertydisposals.models.dms._
import uk.gov.hmrc.cgtpropertydisposals.service.dms.PdfGenerationService
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import java.time.format.DateTimeFormatter
import java.time.{Clock, Instant, LocalDateTime, ZoneId}
import java.util.Base64

class DmsConnectorSpec
    extends AnyWordSpec
    with Matchers
    with IdiomaticMockito
    with WireMockSupport
    with GuiceOneAppPerSuite {
  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
        |microservice {
        |  services {
        |        dms {
        |            port = $wireMockPort
        |        }
        |  }
        |}
        |create-internal-auth-token-on-start = false
        |""".stripMargin
    )
  )

  private val blankHtml =
    """
      |<!DOCTYPE html>
      |<html lang="en">
      |
      |<head>
      |    <title>Blah</title>
      |</head>
      |
      |<body>
      |
      |<div class="container">
      |   <p>Blah</p>
      |</div>
      |
      |</body>
      |</html>
      |""".stripMargin

  private val encodedHtml = Base64.getEncoder.encodeToString(blankHtml.getBytes)

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val clock = Clock.fixed(Instant.parse("2014-12-22T10:15:30Z"), ZoneId.of("UTC"))

  private val mockPdfGenerationService = mock[PdfGenerationService]

  private val mockPdfBytes = "some pdf stuff".getBytes

  mockPdfGenerationService.generatePDFBytes(blankHtml) returns mockPdfBytes

  private val dmsSubmissionPayload = DmsSubmissionPayload(
    B64Html(encodedHtml),
    List(FileAttachment("key", "filename", Some("application/pdf"), Seq(ByteString("data")))),
    sample[DmsMetadata]
  )

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(config)
    .overrides(
      bind[Clock] to clock,
      bind[PdfGenerationService] to mockPdfGenerationService
    )
    .build()

  class Setup {
    val connector: DmsConnector = app.injector.instanceOf[DmsConnector]
  }

  "Dms Connector" should {
    "return an error if the http call fails" in new Setup {
      stubFor(
        stubHttpRequest(dmsSubmissionPayload).willReturn(
          aResponse().withFault(Fault.EMPTY_RESPONSE)
        )
      )

      intercept[Throwable](
        await(connector.submitToDms(dmsSubmissionPayload)) shouldBe Left(Error(""))
      )
    }

    "return an error if the downstream returns an error" in new Setup {
      List(
        HttpResponse(400, "Received response status 400 from dms service"),
        HttpResponse(500, "Received response status 500 from dms service")
      ).foreach { httpResponse =>
        withClue(s"For http response [${httpResponse.toString}]") {
          stubFor(
            stubHttpRequest(dmsSubmissionPayload).willReturn(
              aResponse().withStatus(httpResponse.status).withBody(httpResponse.body)
            )
          )

          intercept[UpstreamErrorResponse](await(connector.submitToDms(dmsSubmissionPayload)))
        }
      }
    }

    "return an envelope id if downstream returns success" in new Setup {
      stubFor(
        stubHttpRequest(dmsSubmissionPayload).willReturn(
          aResponse().withStatus(202).withBody(Json.obj("id" -> "test envelope id").toString())
        )
      )

      await(connector.submitToDms(dmsSubmissionPayload)) shouldBe
        DmsEnvelopeId("test envelope id")
    }
  }

  private def stubHttpRequest(dmsSubmissionPayload: DmsSubmissionPayload) =
    post(urlPathMatching("/dms-submission/submit"))
      .withHeader(AUTHORIZATION, equalTo("9b3a4d91-1ba5-45a9-abe6-ee820ff57bab"))
      .withMultipartRequestBody(
        aMultipart()
          .withName("callbackUrl")
          .withBody(equalTo("http://localhost:7021/cgt-property-disposals/dms/callback"))
      )
      .withMultipartRequestBody(aMultipart().withName("metadata.source").withBody(equalTo("cgtpd")))
      .withMultipartRequestBody(
        aMultipart()
          .withName("metadata.timeOfReceipt")
          .withBody(equalTo(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now(clock))))
      )
      .withMultipartRequestBody(
        aMultipart().withName("metadata.formId").withBody(equalTo(dmsSubmissionPayload.dmsMetadata.dmsFormId))
      )
      .withMultipartRequestBody(
        aMultipart().withName("metadata.customerId").withBody(equalTo(dmsSubmissionPayload.dmsMetadata.customerId))
      )
      .withMultipartRequestBody(
        aMultipart()
          .withName("metadata.classificationType")
          .withBody(equalTo(dmsSubmissionPayload.dmsMetadata.classificationType))
      )
      .withMultipartRequestBody(
        aMultipart()
          .withName("metadata.businessArea")
          .withBody(equalTo(dmsSubmissionPayload.dmsMetadata.businessArea))
      )
      .withMultipartRequestBody(aMultipart().withName("form").withBody(binaryEqualTo(mockPdfBytes)))
}
