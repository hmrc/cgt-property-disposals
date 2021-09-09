/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.ws
import play.api.libs.ws.ahc.AhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseStatus}
import play.api.libs.ws.{BodyWritable, WSResponse}
import play.api.test.Helpers.{await, _}
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.Response
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.http.PlayHttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.dms._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GFormConnectorSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport {

  val config = Configuration(
    ConfigFactory.parseString(
      """
        |microservice {
        |  services {
        |          gform {
        |            host = localhost
        |            port = 9196
        |        }
        |  }
        |}
        |""".stripMargin
    )
  )

  val mockWsClient = mock[PlayHttpClient]

  private def buildWsResponse(status: Int, body: String): WSResponse = {
    val responseBuilder = new Response.ResponseBuilder()
    responseBuilder.accumulate(
      new CacheableHttpResponseStatus(Uri.create("https://gforms"), status, "status text", "protocols")
    )
    responseBuilder.accumulate(new DefaultHttpHeaders().add("my-header", "value"))
    responseBuilder.accumulate(new CacheableHttpResponseBodyPart(body.getBytes(), true))

    new AhcWSResponse(responseBuilder.build())
  }

  def mockPost[A](url: String, headers: Seq[(String, String)])(
    response: Future[ws.WSResponse]
  ) =
    (mockWsClient
      .post(_: String, _: Seq[(String, String)], _: A)(_: BodyWritable[A]))
      .expects(url, headers, *, *)
      .returning(response)

  val connector = new GFormConnectorImpl(mockWsClient, new ServicesConfig(config))

  "GForm Connector" when {

    "return an error if the http call to download the file fails" in {

      val dms = DmsSubmissionPayload(
        B64Html("html"),
        List(FileAttachment("key", "filename", Some("application/pdf"), Seq(ByteString("data")))),
        sample[DmsMetadata]
      )
      val id  = UUID.randomUUID()

      List(
        buildWsResponse(400, "error body"),
        buildWsResponse(500, "error body")
      ).foreach { httpResponse =>
        withClue(s"For http response [${httpResponse.toString}]") {
          mockPost(
            "http://localhost:9196/gform/dms/submit-with-attachments",
            Seq.empty
          )(Future.successful(httpResponse))
          await(connector.submitToDms(dms, id).value) shouldBe Left(Error("error body"))
        }
      }
    }

    "return an envelope id if successful call is made" in {

      val dms = DmsSubmissionPayload(
        B64Html("html"),
        List.empty,
        sample[DmsMetadata]
      )

      List(
        buildWsResponse(200, "id")
      ).foreach { httpResponse =>
        withClue(s"For http response [${httpResponse.toString}]") {
          mockPost(
            "http://localhost:9196/gform/dms/submit-with-attachments",
            Seq.empty
          )(Future.successful(httpResponse))
          await(
            connector
              .submitToDms(
                dms,
                UUID.randomUUID()
              )
              .value
          ) shouldBe Right(EnvelopeId("id"))
        }
      }
    }

  }

}
