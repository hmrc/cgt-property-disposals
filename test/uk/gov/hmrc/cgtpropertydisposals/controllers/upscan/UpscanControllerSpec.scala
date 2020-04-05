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

package uk.gov.hmrc.cgtpropertydisposals.controllers.upscan

import java.time.LocalDateTime

import akka.stream.Materializer
import cats.data.EitherT
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Headers, WrappedRequest}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.{AuthenticateActions, AuthenticatedRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.models.upscan._
import uk.gov.hmrc.cgtpropertydisposals.service.upscan.UpscanService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpscanControllerSpec extends ControllerSpec with ScalaCheckDrivenPropertyChecks {

  val mockUpscanService: UpscanService = mock[UpscanService]
  val fixedTimestamp                   = LocalDateTime.of(2019, 9, 24, 15, 47, 20)

  override val overrideBindings: List[GuiceableModule] =
    List(
      bind[AuthenticateActions].toInstance(Fake.login(Fake.user, fixedTimestamp)),
      bind[UpscanService].toInstance(mockUpscanService)
    )

  implicit lazy val mat: Materializer = fakeApplication.materializer

  val headerCarrier = HeaderCarrier()

  def mockStoreUpscanUpload(upscanUpload: UpscanUpload)(
    response: Either[Error, Unit]
  ) =
    (mockUpscanService
      .storeUpscanUpload(_: UpscanUpload))
      .expects(upscanUpload)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockUpdateUpscanUpload(
    upscanReference: UpscanReference,
    upscanUpload: UpscanUpload
  )(
    response: Either[Error, Unit]
  ) =
    (mockUpscanService
      .updateUpscanUpload(_: UpscanReference, _: UpscanUpload))
      .expects(upscanReference, upscanUpload)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockGetUpscanUpload(upscanReference: UpscanReference)(
    response: Either[Error, Option[UpscanUpload]]
  ) =
    (mockUpscanService
      .readUpscanUpload(_: UpscanReference))
      .expects(upscanReference)
      .returning(EitherT[Future, Error, Option[UpscanUpload]](Future.successful(response)))

  def mockGetUpscanUploads(upscanReferences: List[UpscanReference])(
    response: Either[Error, List[UpscanUpload]]
  ) =
    (mockUpscanService
      .readUpscanUploads(_: List[UpscanReference]))
      .expects(upscanReferences)
      .returning(EitherT[Future, Error, List[UpscanUpload]](Future.successful(response)))

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  def fakeRequestWithJsonBody(body: JsValue): WrappedRequest[JsValue] =
    request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new UpscanController(
    authenticate  = Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    upscanService = mockUpscanService,
    cc            = Helpers.stubControllerComponents()
  )

  val upscanReference = sample[UpscanReference]

  "Upscan Controller" when {

    "it receives a request to get an upscan upload" must {

      "return an internal server error if the backend call fails" in {
        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest()
          )

        mockGetUpscanUpload(upscanReference)(Left(Error("mongo error")))

        val result = controller.getUpscanUpload(upscanReference)(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return a bad request if the store descriptor structure is corrupted" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest()
          )

        mockGetUpscanUpload(upscanReference)(Right(None))

        val result = controller.getUpscanUpload(upscanReference)(request)
        status(result) shouldBe BAD_REQUEST

      }

      "return a 200 OK if the backend call succeeds" in {

        val upscanUpload = sample[UpscanUpload]

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest()
          )

        mockGetUpscanUpload(upscanReference)(Right(Some(upscanUpload)))

        val result = controller.getUpscanUpload(upscanReference)(request)
        status(result) shouldBe OK

      }

    }

    "it receives a request to get upscan uploads" must {

      "return an internal server error if the backend call fails" in {
        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest()
          ).withBody(Json.parse(s"""{ "upscanReferences" : [ ${Json.toJson(upscanReference)} ] }"""))

        mockGetUpscanUploads(List(upscanReference))(Left(Error("mongo error")))

        val result = controller.getUpscanUploads()(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return a bad request if the JSON body cannot be parsed" in {
        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest()
          ).withBody(Json.parse("""{ "things" : "other things" }"""))

        val result = controller.getUpscanUploads()(request)
        status(result) shouldBe BAD_REQUEST
      }

      "return a 200 OK if the backend call succeeds" in {

        val upscanUpload = sample[UpscanUpload]

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest()
          ).withBody(Json.parse(s"""{ "upscanReferences" : [ ${Json.toJson(upscanReference)} ] }"""))

        mockGetUpscanUploads(List(upscanReference))(Right(List(upscanUpload)))

        val result = controller.getUpscanUploads()(request)
        status(result) shouldBe OK

      }

    }

    "it receives a request to save an upscan upload" must {

      "return an internal server error if the backend call fails" in {

        val upscanUploadPayload =
          """
            |{
            |    "upscanUploadMeta" : {
            |        "reference" : "glwibAzzhpamXyavalyif",
            |        "uploadRequest" : {
            |            "href" : "wveovofmaobqq",
            |            "fields" : {}
            |        }
            |    },
            |    "uploadedOn" : "1970-01-01T01:00:07.665",
            |    "upscanUploadStatus" : {
            |        "Initiated" : {}
            |    }
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanUploadPayload))
          )

        val upscanUpload = Json.parse(upscanUploadPayload).as[UpscanUpload]

        mockStoreUpscanUpload(upscanUpload)(Left(Error("mongo error")))

        val result = controller.saveUpscanUpload()(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return a bad request if the request payload is incorrect" in {

        val badUpscanUploadPayload =
          """
            |{
            |    "upscanUploadMeta" : {
            |        "reference" : "glwibAzzhpamXyavalyif",
            |        "uploadRequest" : {
            |            "href" : "wveovofmaobqq",
            |            "fields" : {}
            |        }
            |    },
            |    "upscanUploadStatus" : {
            |        "Initiated" : {}
            |    }
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(badUpscanUploadPayload))
          )

        val result = controller.saveUpscanUpload()(request)
        status(result) shouldBe BAD_REQUEST
      }

      "return an 200 OK response if a valid request is received" in {

        val upscanUploadPayload =
          """
            |{
            |    "upscanUploadMeta" : {
            |        "reference" : "glwibAzzhpamXyavalyif",
            |        "uploadRequest" : {
            |            "href" : "wveovofmaobqq",
            |            "fields" : {}
            |        }
            |    },
            |    "uploadedOn" : "1970-01-01T01:00:07.665",
            |    "upscanUploadStatus" : {
            |        "Initiated" : {}
            |    }
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanUploadPayload))
          )

        val upscanUpload = Json.parse(upscanUploadPayload).as[UpscanUpload]

        mockStoreUpscanUpload(upscanUpload)(Right(()))

        val result = controller.saveUpscanUpload()(request)
        status(result) shouldBe OK
      }
    }

    "it receives a request to update an upscan upload" must {

      "return an internal server error if the backend call fails" in {

        val upscanUploadPayload =
          """
              |{
              |    "upscanUploadMeta" : {
              |        "reference" : "glwibAzzhpamXyavalyif",
              |        "uploadRequest" : {
              |            "href" : "wveovofmaobqq",
              |            "fields" : {}
              |        }
              |    },
              |    "uploadedOn" : "1970-01-01T01:00:07.665",
              |    "upscanUploadStatus" : {
              |        "Initiated" : {}
              |    }
              |}
              |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanUploadPayload))
          )

        val upscanUpload = Json.parse(upscanUploadPayload).as[UpscanUpload]

        mockUpdateUpscanUpload(
          UpscanReference(upscanUpload.upscanUploadMeta.reference),
          upscanUpload
        )(Left(Error("mongo error")))

        val result = controller.updateUpscanUpload(
          UpscanReference(upscanUpload.upscanUploadMeta.reference)
        )(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return a bad request if the request payload is incorrect" in {

        val upscanReference = sample[UpscanReference]

        val badUpscanUploadPayload =
          """
              |{
              |    "upscanUploadMeta" : {
              |        "reference" : "glwibAzzhpamXyavalyif",
              |        "uploadRequest" : {
              |            "href" : "wveovofmaobqq",
              |            "fields" : {}
              |        }
              |    },
              |    "upscanUploadStatus" : {
              |        "Initiated" : {}
              |    }
              |}
              |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(badUpscanUploadPayload))
          )

        val result = controller.updateUpscanUpload(upscanReference)(request)
        status(result) shouldBe BAD_REQUEST
      }

      "return an 200 OK response if a valid request is received" in {

        val upscanReference = sample[UpscanReference]

        val upscanUploadPayload =
          """
              |{
              |    "upscanUploadMeta" : {
              |        "reference" : "glwibAzzhpamXyavalyif",
              |        "uploadRequest" : {
              |            "href" : "wveovofmaobqq",
              |            "fields" : {}
              |        }
              |    },
              |    "uploadedOn" : "1970-01-01T01:00:07.665",
              |    "upscanUploadStatus" : {
              |        "Initiated" : {}
              |    }
              |}
              |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanUploadPayload))
          )

        val upscanUpload = Json.parse(upscanUploadPayload).as[UpscanUpload]

        mockUpdateUpscanUpload(upscanReference, upscanUpload)(Right(()))

        val result = controller.updateUpscanUpload(upscanReference)(request)
        status(result) shouldBe OK
      }
    }

    "it receives a upscan call back request" must {

      "return an internal server error if the payload does not contain a upscan status" in {

        val upscanReference = sample[UpscanReference]

        val upscanUploadPayload =
          """
            |{
            |    "upscanUploadMeta" : {
            |        "reference" : "glwibAzzhpamXyavalyif",
            |        "uploadRequest" : {
            |            "href" : "wveovofmaobqq",
            |            "fields" : {}
            |        }
            |    },
            |    "uploadedOn" : "1970-01-01T01:00:07.665",
            |    "upscanUploadStatus" : {
            |        "Initiated" : {}
            |    }
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanUploadPayload))
          )

        val result = controller.callback(
          upscanReference
        )(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return an internal server error if the payload does not contain a valid status" in {

        val upscanReference = sample[UpscanReference]

        val upscanUploadPayload =
          """
            |{
            |    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
            |    "fileStatus" : "SOME BAD STATUS",
            |    "downloadUrl" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            |    "uploadDetails": {
            |        "uploadTimestamp": "2018-04-24T09:30:00Z",
            |        "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
            |        "fileName": "test.pdf",
            |        "fileMimeType": "application/pdf"
            |    }
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanUploadPayload))
          )

        val result = controller.callback(
          upscanReference
        )(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return an internal server error if the payload contains a valid status" in {

        val upscanReference = UpscanReference("11370e18-6e24-453e-b45a-76d3e32ea33d")
        val upscanUpload    = sample[UpscanUpload]
        val upscanSuccess = sample[UpscanSuccess].copy(
          reference   = upscanReference.value,
          fileStatus  = "READY",
          downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          uploadDetails = Map(
            ("uploadTimestamp", "2018-04-24T09:30:00Z"),
            ("checksum", "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"),
            ("fileName", "test.pdf"),
            ("fileMimeType", "application/pdf")
          )
        )

        val upscanCallBackReequest =
          """
            |{
            |    "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d",
            |    "fileStatus" : "READY",
            |    "downloadUrl" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            |    "uploadDetails": {
            |        "uploadTimestamp": "2018-04-24T09:30:00Z",
            |        "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
            |        "fileName": "test.pdf",
            |        "fileMimeType": "application/pdf"
            |    }
            |}
            |""".stripMargin

        inSequence {
          mockGetUpscanUpload(upscanReference)(Right(Some(upscanUpload)))
          mockUpdateUpscanUpload(
            upscanReference,
            upscanUpload.copy(upscanCallBack = Some(upscanSuccess.copy(reference = upscanReference.value)))
          )(Right(()))
        }

        val result = controller.callback(
          upscanReference
        )(fakeRequestWithJsonBody(Json.parse(upscanCallBackReequest)))
        status(result) shouldBe NO_CONTENT
      }

    }

  }
}
