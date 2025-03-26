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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ahc.AhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseStatus}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.Response
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionPollerExecutionContext
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class S3ConnectorSpec
    extends AnyWordSpec
    with Matchers
    with IdiomaticMockito
    with WireMockSupport
    with WireMockMethods
    with GuiceOneAppPerSuite {

  private val config = Configuration(
    ConfigFactory.parseString(
      """
        | s3 {
        |   file-download-timeout = 2 minutes
        |   upstream-element-limit-scale-factor = 200
        |   max-file-download-size-in-mb = 5
        | }
        |""".stripMargin
    )
  )

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  private def buildWsResponse(status: Int): WSResponse = {
    val responseBuilder = new Response.ResponseBuilder()
    responseBuilder.accumulate(
      new CacheableHttpResponseStatus(
        Uri.create("https://bucketname.s3.eu-west-2.amazonaws.com"),
        status,
        "status text",
        "protocols!"
      )
    )
    responseBuilder.accumulate(new DefaultHttpHeaders().add("My-Header", "value"))
    responseBuilder.accumulate(new CacheableHttpResponseBodyPart("error body".getBytes(), true))

    new AhcWSResponse(responseBuilder.build())
  }

  implicit val hc: HeaderCarrier                                       = HeaderCarrier()
  implicit val actorSystem: ActorSystem                                = ActorSystem()
  implicit val dmsExectionContext: DmsSubmissionPollerExecutionContext = new DmsSubmissionPollerExecutionContext(
    actorSystem
  )

  val connector: S3Connector = app.injector.instanceOf[S3Connector]

  override def afterAll(): Unit = {
    Await.ready(actorSystem.terminate(), 10 seconds)
    super.afterAll()
  }

  val downloadUrl: String => String = (path: String) => s"http://$wireMockHost:$wireMockPort/$path"

  "Upscan Connector" when {
    "it receives a request to download a file" must {
      "return an error if the http call to download the file fails" in {
        List(
          buildWsResponse(400),
          buildWsResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              GET,
              "/some-url",
              headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
            ).thenReturn(httpResponse.status)

            await(
              connector
                .downloadFile(
                  UpscanSuccess(
                    "ref",
                    "status",
                    downloadUrl("some-url"),
                    Map("fileName" -> "f1.text", "fileMimeType" -> "application/pdf")
                  )
                )
            ) shouldBe Left(Error(s"could not download file from s3"))
          }
        }
      }

      "return an error if the required file descriptors cannot be found" in {
        List(
          buildWsResponse(400),
          buildWsResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            await(
              connector
                .downloadFile(UpscanSuccess("ref", "status", "some-url", Map.empty))
            ) shouldBe Left(Error("missing file descriptors"))
          }
        }
      }

      "return a file attachment record if the file was downloaded successfully " in {
        List(
          buildWsResponse(200)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              GET,
              "/some-url",
              headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
            ).thenReturn(httpResponse.status)

            val result = await(
              connector
                .downloadFile(
                  UpscanSuccess(
                    "ref",
                    "status",
                    downloadUrl("some-url"),
                    Map("fileName" -> "f1.text", "fileMimeType" -> "application/pdf")
                  )
                )
            )
            result.isRight shouldBe true
          }
        }
      }

      "Windows OS filenames" must {
        "return same filename if the file was downloaded successfully" +
          "and it has no Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "sample_test01.edition1.txt", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "sample_test01.edition1.txt"
                  case _                                        => fail()
                }
              }
            }
          }

        "replace '31' ascii chars with hyphen(-) if the file was downloaded successfully" +
          "and it has a Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "31", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "-"
                  case _                                        => fail()
                }
              }
            }
          }

        "replace '01' ascii chars with hyphen(-) if the file was downloaded successfully" +
          "and it has a Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "01", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "-"
                  case _                                        => fail()
                }
              }
            }
          }

        "replace special chars with hyphen(-) if the file was downloaded successfully" +
          "and it has a Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "sample<cgt>file?name/one*.txt", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "sample-cgt-file-name-one-.txt"
                  case _                                        => fail()
                }
              }
            }
          }

        "replace '\"' with hyphen(-) if the file was downloaded successfully" +
          "and it has a Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "sample\"test.txt", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "sample-test.txt"
                  case _                                        => fail()
                }
              }
            }
          }

        "replace '\\' with hyphen(-) if the file was downloaded successfully" +
          "and it has a Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "sample\\test.txt", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "sample-test.txt"
                  case _                                        => fail()
                }
              }
            }
          }

        "check '%' does not cause any issues if the file was downloaded successfully" +
          "and it has a Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "sample%22test.txt", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "sample-22test.txt"
                  case _                                        => fail()
                }
              }
            }
          }

        "replace : with hyphen(-) if the file was downloaded successfully" +
          "and it has a Windows OS invalid character" in {
            List(
              buildWsResponse(200)
            ).foreach { httpResponse =>
              withClue(s"For http response [${httpResponse.toString}]") {
                when(
                  GET,
                  "/some-url",
                  headers = Seq("User-Agent" -> "cgt-property-disposals").toMap
                ).thenReturn(httpResponse.status)

                val result = await(
                  connector
                    .downloadFile(
                      UpscanSuccess(
                        "ref",
                        "status",
                        downloadUrl("some-url"),
                        Map("fileName" -> "sample:test.txt", "fileMimeType" -> "text/plain")
                      )
                    )
                )

                result match {
                  case Right(FileAttachment(_, filename, _, _)) => filename shouldBe "sample-test.txt"
                  case _                                        => fail()
                }
              }
            }
          }
      }
    }
  }
}
