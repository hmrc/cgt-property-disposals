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
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, DraftReturnId}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanFileDescriptor.UpscanFileDescriptorStatus.UPLOADED
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanStatus.READY
import uk.gov.hmrc.cgtpropertydisposals.models.upscan._
import uk.gov.hmrc.cgtpropertydisposals.service.UpscanService
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

  def mockStoreUpscanFileDescriptor(upscanFileDescriptor: UpscanFileDescriptor)(
    response: Either[Error, Unit]
  ) =
    (mockUpscanService
      .storeFileDescriptorData(_: UpscanFileDescriptor))
      .expects(upscanFileDescriptor)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockUpdateFileDescriptor(upscanFileDescriptor: UpscanFileDescriptor)(
    response: Either[Error, Boolean]
  ) =
    (mockUpscanService
      .updateUpscanFileDescriptorStatus(_: UpscanFileDescriptor))
      .expects(upscanFileDescriptor)
      .returning(EitherT[Future, Error, Boolean](Future.successful(response)))

  def mockStoreUpscanCallBack(upscanCallBack: UpscanCallBack)(
    response: Either[Error, Boolean]
  ) =
    (mockUpscanService
      .saveCallBackData(_: UpscanCallBack))
      .expects(upscanCallBack)
      .returning(EitherT[Future, Error, Boolean](Future.successful(response)))

  def mockGetUpscanFileDescriptor(draftReturnId: DraftReturnId, upscanInitiateReference: UpscanInitiateReference)(
    response: Either[Error, Option[UpscanFileDescriptor]]
  ) =
    (mockUpscanService
      .getUpscanFileDescriptor(_: DraftReturnId, _: UpscanInitiateReference))
      .expects(draftReturnId, upscanInitiateReference)
      .returning(EitherT[Future, Error, Option[UpscanFileDescriptor]](Future.successful(response)))

  def mockGetUpscanSnapshot(draftReturnId: DraftReturnId)(
    response: Either[Error, UpscanSnapshot]
  ) =
    (mockUpscanService
      .getUpscanSnapshot(_: DraftReturnId))
      .expects(draftReturnId)
      .returning(EitherT[Future, Error, UpscanSnapshot](Future.successful(response)))

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

  val upfd = UpscanFileDescriptor(
    upscanInitiateReference = UpscanInitiateReference("12345"),
    draftReturnId           = DraftReturnId("draft-return-id"),
    cgtReference            = CgtReference("cgt-ref"),
    fileDescriptor = FileDescriptor(
      reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
      uploadRequest = UploadRequest(
        href = "https://xxxx/upscan-upload-proxy/bucketName",
        fields = Map(
          "Content-Type"            -> "application/xml",
          "acl"                     -> "private",
          "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
          "policy"                  -> "xxxxxxxx==",
          "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
          "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-date"              -> "yyyyMMddThhmmssZ",
          "x-amz-meta-callback-url" -> "https://myservice.com/callback",
          "x-amz-signature"         -> "xxxx"
        )
      )
    ),
    timestamp = LocalDateTime.of(2020, 2, 19, 16, 24, 16),
    status    = UPLOADED
  )
  val upscanFileDescriptorPayload =
    """
      |{
      |   "upscanInitiateReference":{
      |      "value":"12345"
      |   },
      |   "draftReturnId":{
      |      "value":"draft-return-id"
      |   },
      |   "cgtReference":{
      |      "value":"cgt-ref"
      |   },
      |   "fileDescriptor":{
      |      "reference":"11370e18-6e24-453e-b45a-76d3e32ea33d",
      |      "uploadRequest":{
      |         "href":"https://xxxx/upscan-upload-proxy/bucketName",
      |         "fields":{
      |            "x-amz-meta-callback-url":"https://myservice.com/callback",
      |            "x-amz-date":"yyyyMMddThhmmssZ",
      |            "x-amz-credential":"ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
      |            "x-amz-algorithm":"AWS4-HMAC-SHA256",
      |            "key":"xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      |            "acl":"private",
      |            "x-amz-signature":"xxxx",
      |            "Content-Type":"application/xml",
      |            "policy":"xxxxxxxx=="
      |         }
      |      }
      |   },
      |   "timestamp":"2020-02-19T16:24:16",
      |   "status":{
      |      "UPLOADED":{
      |
      |      }
      |   }
      |}
      |""".stripMargin

  val validUpscanCallBackPayload =
    """
      | {
      |   "draftReturnId":{
      |      "value":"draft-return-id"
      |   },
      |   "cgtReference" : {
      |     "value" : "cgt-ref"
      |   },
      |   "callbackResult": {
      |   "reference":"11370e18-6e24-453e-b45a-76d3e32ea33d",
      |   "fileStatus":{"READY_TO_UPLOAD":{}},
      |   "downloadUrl":"http://aws.com/file",
      |   "checksum":"12345",
      |   "fileName":"test.pdf",
      |   "fileMimeType":"application/pdf"
      |   }
      | }
      |
      |""".stripMargin

  val upscanCallBack = UpscanCallBack(
    draftReturnId = DraftReturnId("draft-return-id"),
    reference     = "11370e18-6e24-453e-b45a-76d3e32ea33d",
    fileStatus    = READY,
    downloadUrl   = Some("http://aws.com/file"),
    details = Map(
      "Content-Type"            -> "application/xml",
      "acl"                     -> "private",
      "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "policy"                  -> "xxxxxxxx==",
      "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
      "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
      "x-amz-date"              -> "yyyyMMddThhmmssZ",
      "x-amz-meta-callback-url" -> "https://myservice.com/callback",
      "x-amz-signature"         -> "xxxx"
    )
  )

  val draftId   = sample[DraftReturnId]
  val upscanRef = sample[UpscanInitiateReference]

  "Upscan Controller" when {

    "it receives a request to update upscan file descriptor status" must {

      "return a bad request if the request contains an incorrect payload" in {
        val corruptRequestBody =
          """
            |{
            |   "bad-field":"bad-value"
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(corruptRequestBody))
          )

        val result = controller.updateUpscanFileDescriptorStatus()(request)
        status(result) shouldBe BAD_REQUEST

      }

      "return an internal server error if the backend call fails" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockUpdateFileDescriptor(upfd)(Left(Error("BE error")))

        val result = controller.updateUpscanFileDescriptorStatus()(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR

      }

      "return a 200 OK if the backend call succeeds" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockUpdateFileDescriptor(upfd)(Right(true))

        val result = controller.updateUpscanFileDescriptorStatus()(request)
        status(result) shouldBe OK

      }

    }

    "it receives a request to get the upscan file descriptor" must {

      "return an internal server error if the backend call fails" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockGetUpscanFileDescriptor(draftId, upscanRef)(Left(Error("mongo error")))

        val result = controller.getUpscanFileDescriptor(draftId.value, upscanRef.value)(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR

      }

      "return a bad request if the store descriptor structure is corrupted" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockGetUpscanFileDescriptor(draftId, upscanRef)(Right(None))

        val result = controller.getUpscanFileDescriptor(draftId.value, upscanRef.value)(request)
        status(result) shouldBe BAD_REQUEST

      }

      "return a 200 OK if the backend call succeeds" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockGetUpscanFileDescriptor(draftId, upscanRef)(Right(Some(upfd)))

        val result = controller.getUpscanFileDescriptor(draftId.value, upscanRef.value)(request)
        status(result) shouldBe OK

      }

    }

    "it receives a request to get an upscan snapshot" must {

      "return an internal server error if the backend call fails" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockGetUpscanSnapshot(DraftReturnId("ref"))(Left(Error("mongo error")))

        val result = controller.getUpscanSnapshot(DraftReturnId("ref"))(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR

      }

      "return a bad request if the store descriptor structure is corrupted" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockGetUpscanSnapshot(DraftReturnId("ref"))(Left(Error("mongo error")))

        val result = controller.getUpscanSnapshot(DraftReturnId("ref"))(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR

      }

      "return a 200 OK if the backend call succeeds" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockGetUpscanSnapshot(DraftReturnId("ref"))(Right(UpscanSnapshot(1)))

        val result = controller.getUpscanSnapshot(DraftReturnId("ref"))(request)
        status(result) shouldBe OK

      }

    }

    "it receives a request to save upscan file descriptor" must {

      "return a bad request if the request contains an incorrect payload" in {

        val corruptRequestBody =
          """
            |{
            |   "bad-field":"bad-value"
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(corruptRequestBody))
          )

        val result = controller.saveUpscanFileDescriptor()(request)
        status(result) shouldBe BAD_REQUEST
      }

      "return an internal server error if the backend call fails" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockStoreUpscanFileDescriptor(upfd)(Left(Error("BE error")))

        val result = controller.saveUpscanFileDescriptor()(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR

      }

      "return a 200 OK if the backend call succeeds" in {

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            fakeRequestWithJsonBody(Json.parse(upscanFileDescriptorPayload))
          )

        mockStoreUpscanFileDescriptor(upfd)(Right(()))

        val result = controller.saveUpscanFileDescriptor()(request)
        status(result) shouldBe OK

      }
    }
  }
}
