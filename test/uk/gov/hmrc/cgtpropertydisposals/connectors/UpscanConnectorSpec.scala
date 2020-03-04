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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import com.typesafe.config.ConfigFactory
import org.scalamock.handlers.CallHandler3
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.ws
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ahc.AhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseHeaders, CacheableHttpResponseStatus}
import play.api.test.Helpers.{await, _}
import play.api.{Configuration, Mode}
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.Response
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import uk.gov.hmrc.cgtpropertydisposals.http.PlayHttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanStatus.READY
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, _}

class UpscanConnectorSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val config = Configuration(
    ConfigFactory.parseString(
      """
        | dms = {
        |   s3-file-download-timeout = 2 minutes
        | }
        |""".stripMargin
    )
  )

  val mockWsClient = mock[PlayHttpClient]

  private def buildWsResponse(status: Int): WSResponse =
    new AhcWSResponse(
      new Response.ResponseBuilder()
        .accumulate(
          new CacheableHttpResponseStatus(
            Uri.create("https://bucketname.s3.eu-west-2.amazonaws.com"),
            status,
            "status text",
            "protocols!"
          )
        )
        .accumulate(new CacheableHttpResponseHeaders(false, new DefaultHttpHeaders().add("My-Header", "value")))
        .accumulate(new CacheableHttpResponseBodyPart("error body".getBytes(), true))
        .build()
    )

  def mockGet(url: String, headers: Seq[(String, String)], timeout: Duration)(
    response: Future[ws.WSResponse]
  ): CallHandler3[String, Seq[(String, String)], Duration, Future[ws.WSResponse]] =
    (mockWsClient
      .get(_: String, _: Seq[(String, String)], _: Duration))
      .expects(url, headers, timeout)
      .returning(response)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val connector = new UpscanConnectorImpl(mockWsClient, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  "Upscan Connector" when {

    "it receives a request to download a file" must {
      "return an error if the http call to download the file fails" in {
        List(
          buildWsResponse(400),
          buildWsResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockGet(
              "some-url",
              Seq(("User-Agent" -> "cgt-property-disposal")),
              2 minutes
            )(Future.successful(httpResponse))
            await(
              connector
                .downloadFile(UpscanCallBack(CgtReference(""), "", READY, Some("some-url"), Map.empty))
            ) shouldBe Left(Error(s"download failed with status ${httpResponse.status}"))
          }
        }
      }

      "return a file attachment record if the file was downloaded successfully " in {
        List(
          buildWsResponse(200)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockGet(
              "some-url",
              Seq(("User-Agent" -> "cgt-property-disposal")),
              2 minutes
            )(Future.successful(httpResponse))
            await(
              connector
                .downloadFile(
                  UpscanCallBack(
                    CgtReference("ref"),
                    "ref-1",
                    READY,
                    Some("some-url"),
                    Map(("filename" -> "f1.text"), ("fileMimeType" -> "application/pdf"))
                  )
                )
            ).isRight shouldBe true
          }
        }
      }
    }
  }
}
