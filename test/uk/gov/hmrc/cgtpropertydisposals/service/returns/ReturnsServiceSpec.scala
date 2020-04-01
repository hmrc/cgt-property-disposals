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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import java.time.{LocalDate, LocalDateTime, LocalTime}

import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.account.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.ReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{DesReturnDetails, DesSubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{DesFinancialDataResponse, DesFinancialTransaction}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{AgentReferenceNumber, CgtReference}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse.ReturnCharge
import uk.gov.hmrc.cgtpropertydisposals.models.returns.audit.{ReturnConfirmationEmailSentEvent, SubmitReturnEvent, SubmitReturnResponseEvent}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, ReturnSummary, SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.{DesListReturnsResponse, DesReturnSummary}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.transformers.{ReturnSummaryListTransformerService, ReturnTransformerService}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsServiceSpec extends WordSpec with Matchers with MockFactory {

  val returnsConnector = mock[ReturnsConnector]

  val mockFinancialDataConnector = mock[FinancialDataConnector]

  val mockReturnTransformerService = mock[ReturnTransformerService]

  val mockReturnListSummaryTransformerService = mock[ReturnSummaryListTransformerService]

  val mockAuditService = mock[AuditService]

  val mockEmailConnector = mock[EmailConnector]

  val config = Configuration(
    ConfigFactory.parseString(
      """
        |des.non-iso-country-codes = []
        |""".stripMargin
    )
  )

  val returnsService =
    new DefaultReturnsService(
      returnsConnector,
      mockFinancialDataConnector,
      mockReturnTransformerService,
      mockReturnListSummaryTransformerService,
      mockEmailConnector,
      mockAuditService,
      config,
      MockMetrics.metrics
    )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val request: Request[_] = FakeRequest()

  def mockSubmitReturn(cgtReference: CgtReference, returnRequest: DesSubmitReturnRequest)(
    response: Either[Error, HttpResponse]
  ) =
    (returnsConnector
      .submit(_: CgtReference, _: DesSubmitReturnRequest)(_: HeaderCarrier))
      .expects(cgtReference, returnRequest, hc)
      .returning(EitherT.fromEither[Future](response))

  def mockListReturn(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    response: Either[Error, HttpResponse]
  ) =
    (returnsConnector
      .listReturns(_: CgtReference, _: LocalDate, _: LocalDate)(_: HeaderCarrier))
      .expects(cgtReference, fromDate, toDate, *)
      .returning(EitherT.fromEither[Future](response))

  def mockDisplayReturn(cgtReference: CgtReference, submissionId: String)(response: Either[Error, HttpResponse]) =
    (returnsConnector
      .displayReturn(_: CgtReference, _: String)(_: HeaderCarrier))
      .expects(cgtReference, submissionId, *)
      .returning(EitherT.fromEither[Future](response))

  def mockGetFinancialData(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    response: Either[Error, HttpResponse]
  ) =
    (mockFinancialDataConnector
      .getFinancialData(_: CgtReference, _: LocalDate, _: LocalDate)(_: HeaderCarrier))
      .expects(cgtReference, fromDate, toDate, *)
      .returning(EitherT.fromEither[Future](response))

  def mockTransformReturn(desReturn: DesReturnDetails)(result: Either[Error, CompleteReturn]) =
    (mockReturnTransformerService
      .toCompleteReturn(_: DesReturnDetails))
      .expects(desReturn)
      .returning(result)

  def mockTransformReturnsList(returns: List[DesReturnSummary], financialData: List[DesFinancialTransaction])(
    result: Either[Error, List[ReturnSummary]]
  ) =
    (mockReturnListSummaryTransformerService
      .toReturnSummaryList(_: List[DesReturnSummary], _: List[DesFinancialTransaction]))
      .expects(returns, financialData)
      .returning(result)

  def mockSendReturnSubmitConfirmationEmail(
    submitReturnResponse: SubmitReturnResponse,
    subscribedDetails: SubscribedDetails
  )(
    response: Either[Error, HttpResponse]
  ) =
    (mockEmailConnector
      .sendReturnSubmitConfirmationEmail(_: SubmitReturnResponse, _: SubscribedDetails)(_: HeaderCarrier))
      .expects(submitReturnResponse, subscribedDetails, hc)
      .returning(EitherT(Future.successful(response)))

  def mockAuditSubmitReturnEvent(
    cgtReference: CgtReference,
    submitReturnRequest: DesSubmitReturnRequest,
    agentReferenceNumber: Option[AgentReferenceNumber]
  ) =
    (mockAuditService
      .sendEvent(_: String, _: SubmitReturnEvent, _: String)(
        _: HeaderCarrier,
        _: Writes[SubmitReturnEvent],
        _: Request[_]
      ))
      .expects(
        "submitReturn",
        SubmitReturnEvent(submitReturnRequest, cgtReference.value, agentReferenceNumber.map(_.value)),
        "submit-return",
        *,
        *,
        *
      )
      .returning(())

  def mockAuditSubmitReturnResponseEvent(httpStatus: Int, responseBody: Option[JsValue]) =
    (mockAuditService
      .sendEvent(_: String, _: SubmitReturnResponseEvent, _: String)(
        _: HeaderCarrier,
        _: Writes[SubmitReturnResponseEvent],
        _: Request[_]
      ))
      .expects(
        "submitReturnResponse",
        SubmitReturnResponseEvent(
          httpStatus,
          responseBody.getOrElse(Json.parse("""{ "body" : "could not parse body as JSON: null" }"""))
        ),
        "submit-return-response",
        *,
        *,
        *
      )
      .returning(())

  def mockAuditReturnConfirmationEmailEvent(email: String, cgtReference: String, submissionId: String) =
    (
      mockAuditService
        .sendEvent(_: String, _: ReturnConfirmationEmailSentEvent, _: String)(
          _: HeaderCarrier,
          _: Writes[ReturnConfirmationEmailSentEvent],
          _: Request[_]
        )
      )
      .expects(
        "returnConfirmationEmailSent",
        ReturnConfirmationEmailSentEvent(
          email,
          cgtReference,
          submissionId
        ),
        "return-confirmation-email-sent",
        *,
        *,
        *
      )
      .returning(())

  "CompleteReturnsService" when {

    "handling submitting returns" should {

      "handle successful submits" when {

        "there is a positive charge" in {
          val formBundleId    = "804123737752"
          val chargeReference = "XCRG9448959757"
          val responseJsonBody =
            Json.parse(s"""
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "chargeType": "Late Penalty",
              |     "chargeReference":"$chargeReference",
              |     "amount":11.0,
              |     "dueDate":"2020-03-11",
              |     "formBundleNumber":"$formBundleId",
              |     "cgtReferenceNumber":"XLCGTP212487578"
              |  }
              |}
              |""".stripMargin)
          val submitReturnResponse = SubmitReturnResponse(
            "804123737752",
            LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47)),
            Some(
              ReturnCharge(
                chargeReference,
                AmountInPence(1100L),
                LocalDate.of(2020, 3, 11)
              )
            )
          )
          val submitReturnRequest    = sample[SubmitReturnRequest]
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, Some(responseJsonBody))))
            mockAuditSubmitReturnResponseEvent(200, Some(responseJsonBody))
            mockSendReturnSubmitConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(
              Right(HttpResponse(ACCEPTED))
            )
            mockAuditReturnConfirmationEmailEvent(
              submitReturnRequest.subscribedDetails.emailAddress.value,
              submitReturnRequest.subscribedDetails.cgtReference.value,
              formBundleId
            )
          }

          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }

        "there is a negative charge" in {
          val formBundleId = "804123737752"
          val responseJsonBody =
            Json.parse(s"""
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "chargeType": "Late Penalty",
              |     "chargeReference":"XCRG9448959757",
              |     "amount":-11.0,
              |     "dueDate":"2020-03-11",
              |     "formBundleNumber":"$formBundleId",
              |     "cgtReferenceNumber":"XLCGTP212487578"
              |  }
              |}
              |""".stripMargin)

          val submitReturnResponse = SubmitReturnResponse(
            formBundleId,
            LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47)),
            None
          )
          val submitReturnRequest    = sample[SubmitReturnRequest]
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)
          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, Some(responseJsonBody))))
            mockAuditSubmitReturnResponseEvent(200, Some(responseJsonBody))
            mockSendReturnSubmitConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(
              Right(HttpResponse(ACCEPTED))
            )
            mockAuditReturnConfirmationEmailEvent(
              submitReturnRequest.subscribedDetails.emailAddress.value,
              submitReturnRequest.subscribedDetails.cgtReference.value,
              formBundleId
            )
          }

          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }

        "there is a no charge data" in {
          val formBundleId   = "804123737752"
          val processingDate = LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47))
          val responseJsonBody =
            Json.parse(s"""
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "formBundleNumber":"$formBundleId",
              |     "cgtReferenceNumber":"XLCGTP212487578"
              |  }
              |}
              |""".stripMargin)

          val submitReturnResponse   = SubmitReturnResponse(formBundleId, processingDate, None)
          val submitReturnRequest    = sample[SubmitReturnRequest]
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)
          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, Some(responseJsonBody))))
            mockAuditSubmitReturnResponseEvent(200, Some(responseJsonBody))
            mockSendReturnSubmitConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(
              Right(HttpResponse(ACCEPTED))
            )
            mockAuditReturnConfirmationEmailEvent(
              submitReturnRequest.subscribedDetails.emailAddress.value,
              submitReturnRequest.subscribedDetails.cgtReference.value,
              formBundleId
            )
          }

          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }

        "there is a zero charge" in {
          val formBundleId   = "804123737752"
          val processingDate = LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47))
          val responseJsonBody =
            Json.parse(s"""
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "formBundleNumber":"$formBundleId",
              |     "cgtReferenceNumber":"XLCGTP212487578",
              |     "amount" : 0
              |  }
              |}
              |""".stripMargin)

          val submitReturnResponse   = SubmitReturnResponse(formBundleId, processingDate, None)
          val submitReturnRequest    = sample[SubmitReturnRequest]
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, Some(responseJsonBody))))
            mockAuditSubmitReturnResponseEvent(200, Some(responseJsonBody))
            mockSendReturnSubmitConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(
              Right(HttpResponse(ACCEPTED))
            )
            mockAuditReturnConfirmationEmailEvent(
              submitReturnRequest.subscribedDetails.emailAddress.value,
              submitReturnRequest.subscribedDetails.cgtReference.value,
              formBundleId
            )
          }

          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }
        "there is a positive charge and email call returns 500 and the EmailSent event won't be sent" in {
          val formBundleId    = "804123737752"
          val chargeReference = "XCRG9448959757"
          val responseJsonBody =
            Json.parse(s"""
                          |{
                          |"processingDate":"2020-02-20T09:30:47Z",
                          |"ppdReturnResponseDetails": {
                          |     "chargeType": "Late Penalty",
                          |     "chargeReference":"$chargeReference",
                          |     "amount":11.0,
                          |     "dueDate":"2020-03-11",
                          |     "formBundleNumber":"$formBundleId",
                          |     "cgtReferenceNumber":"XLCGTP212487578"
                          |  }
                          |}
                          |""".stripMargin)
          val submitReturnResponse = SubmitReturnResponse(
            "804123737752",
            LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47)),
            Some(
              ReturnCharge(
                chargeReference,
                AmountInPence(1100L),
                LocalDate.of(2020, 3, 11)
              )
            )
          )
          val submitReturnRequest    = sample[SubmitReturnRequest]
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, Some(responseJsonBody))))
            mockAuditSubmitReturnResponseEvent(200, Some(responseJsonBody))
            mockSendReturnSubmitConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(
              Right(HttpResponse(500))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }
      }

      "return an error" when {

        "there are charge details for a non zero charge amount and " when {

          def test(jsonResponseBody: JsValue): Unit = {
            val submitReturnRequest    = sample[SubmitReturnRequest]
            val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)

            inSequence {
              mockAuditSubmitReturnEvent(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest,
                submitReturnRequest.agentReferenceNumber
              )
              mockSubmitReturn(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest
              )(Right(HttpResponse(200, Some(jsonResponseBody))))
              mockAuditSubmitReturnResponseEvent(200, Some(jsonResponseBody))
            }

            await(returnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
          }

          "the charge amount is missing" in {
            test(
              Json.parse(
                """{
                  |"processingDate":"2020-02-20T09:30:47Z",
                  |"ppdReturnResponseDetails": {
                  |     "chargeType": "Late Penalty",
                  |     "chargeReference":"XCRG9448959757",
                  |     "dueDate":"2020-03-11",
                  |     "formBundleNumber":"804123737752",
                  |     "cgtReferenceNumber":"XLCGTP212487578"
                  |  }
                  |}
                  |""".stripMargin
              )
            )
          }

          "the charge reference is missing" in {
            test(
              Json.parse(
                """{
                  |"processingDate":"2020-02-20T09:30:47Z",
                  |"ppdReturnResponseDetails": {
                  |     "chargeType": "Late Penalty",
                  |     "amount":11.0,
                  |     "dueDate":"2020-03-11",
                  |     "formBundleNumber":"804123737752",
                  |     "cgtReferenceNumber":"XLCGTP212487578"
                  |  }
                  |}
                  |""".stripMargin
              )
            )
          }

          "the charge due date is missing" in {
            test(
              Json.parse(
                """{
                  |"processingDate":"2020-02-20T09:30:47Z",
                  |"ppdReturnResponseDetails": {
                  |     "chargeType": "Late Penalty",
                  |     "chargeReference":"XCRG9448959757",
                  |     "amount":11.0,
                  |     "formBundleNumber":"804123737752",
                  |     "cgtReferenceNumber":"XLCGTP212487578"
                  |  }
                  |}
                  |""".stripMargin
              )
            )
          }

        }

        "the call to submit a return fails" in {
          val submitReturnRequest    = sample[SubmitReturnRequest]
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(
              Left(Error("oh no!"))
            )

          }
          await(returnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
        }

        "the http call comes back with a status other than 200" in {
          val submitReturnRequest    = sample[SubmitReturnRequest]
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(500)))
            mockAuditSubmitReturnResponseEvent(500, None)
          }
          await(returnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
        }
      }
    }

    "handling requests to list returns" must {

      val desListReturnResponseBody = Json.parse(
        s"""
           |{
           |  "processingDate": "2020-03-02T16:09:28Z",
           |  "returnList": [
           |    {
           |      "submissionDate": "2020-02-27",
           |      "submissionId": "130000000581",
           |      "propertyAddress": {
           |        "addressLine1": "49 Argyll Road",
           |        "addressLine3": "LLANBADOC",
           |        "countryCode": "GB",
           |        "postalCode": "NP5 4SW"
           |      },
           |      "completionDate": "2020-01-05",
           |      "taxYear": "2020",
           |      "charges": [
           |        {
           |          "chargeDescription": "CGT PPD Return Non UK Resident",
           |          "chargeAmount": 12813,
           |          "dueDate": "2020-02-04",
           |          "chargeReference": "XD002610151722"
           |        },
           |        {
           |          "chargeDescription": "CGT PPD Late Filing Penalty",
           |          "chargeAmount": 100,
           |          "dueDate": "2020-04-01",
           |          "chargeReference": "XH002610151822"
           |        }
           |      ],
           |      "lastUpdatedDate": "2020-02-27",
           |      "status": "Pending",
           |      "totalCGTLiability": 12913
           |    },
           |    {
           |      "submissionDate": "2020-02-28",
           |      "submissionId": "130000000589",
           |      "propertyAddress": {
           |        "addressLine1": "The Farm",
           |        "addressLine4": "Buckinghamshire",
           |        "addressLine3": "Royal Madeuptown",
           |        "postalCode": "ZZ9Z 9TT",
           |        "countryCode": "GB"
           |      },
           |      "completionDate": "2020-02-02",
           |      "taxYear": "2020",
           |      "charges": [
           |        {
           |          "chargeDescription": "CGT PPD Return UK Resident",
           |          "chargeAmount": 0,
           |          "dueDate": "2020-03-03",
           |          "chargeReference": "XL002610151760"
           |        }
           |      ],
           |      "lastUpdatedDate": "2020-02-28",
           |      "status": "Paid",
           |      "totalCGTLiability": 0
           |    },
           |    {
           |      "submissionDate": "2020-03-02",
           |      "submissionId": "130000000593",
           |      "propertyAddress": {
           |        "addressLine1": "6 Testing Lane",
           |        "addressLine4": "Buckinghamshire",
           |        "addressLine3": "Royal Madeuptown",
           |        "postalCode": "ZZ9Z 9TT",
           |        "countryCode": "GB"
           |      },
           |      "completionDate": "2020-02-01",
           |      "taxYear": "2020",
           |      "charges": [
           |        {
           |          "chargeDescription": "CGT PPD Return UK Resident",
           |          "chargeAmount": 103039,
           |          "dueDate": "2020-03-02",
           |          "chargeReference": "XR002610151861"
           |        }
           |      ],
           |      "lastUpdatedDate": "2020-03-02",
           |      "status": "Pending",
           |      "totalCGTLiability": 103039
           |    }
           |  ]
           |}
          |""".stripMargin
      )

      val desFinancialDataResponse = Json.parse(
        """
          |{
          |  "idType": "ZCGT",
          |  "processingDate": "2020-03-02T16:09:29Z",
          |  "idNumber": "XZCGTP001000257",
          |  "regimeType": "CGT",
          |  "financialTransactions": [
          |    {
          |      "sapDocumentNumberItem": "0001",
          |      "contractObjectType": "CGTP",
          |      "originalAmount": -12812,
          |      "contractObject": "00000250000000000259",
          |      "businessPartner": "0100276998",
          |      "mainTransaction": "5470",
          |      "taxPeriodTo": "2020-04-05",
          |      "periodKey": "19CY",
          |      "items": [
          |        {
          |          "subItem": "000",
          |          "dueDate": "2020-03-03",
          |          "amount": -12812
          |        }
          |      ],
          |      "subTransaction": "1060",
          |      "mainType": "CGT PPD Return UK Resident",
          |      "chargeReference": "XL002610151760",
          |      "contractAccount": "000016001259",
          |      "chargeType": "CGT PPD Return UK Resident",
          |      "taxPeriodFrom": "2019-04-06",
          |      "sapDocumentNumber": "003070004278",
          |      "contractAccountCategory": "16",
          |      "outstandingAmount": -12812,
          |      "periodKeyDescription": "CGT Annual 2019/2020"
          |    },
          |    {
          |      "sapDocumentNumberItem": "0001",
          |      "contractObjectType": "CGTP",
          |      "originalAmount": 103039,
          |      "contractObject": "00000250000000000259",
          |      "businessPartner": "0100276998",
          |      "mainTransaction": "5470",
          |      "taxPeriodTo": "2020-04-05",
          |      "periodKey": "19CY",
          |      "items": [
          |        {
          |          "subItem": "000",
          |          "dueDate": "2020-03-02",
          |          "amount": 103039
          |        }
          |      ],
          |      "subTransaction": "1060",
          |      "mainType": "CGT PPD Return UK Resident",
          |      "chargeReference": "XR002610151861",
          |      "contractAccount": "000016001259",
          |      "chargeType": "CGT PPD Return UK Resident",
          |      "taxPeriodFrom": "2019-04-06",
          |      "sapDocumentNumber": "003100004253",
          |      "contractAccountCategory": "16",
          |      "outstandingAmount": 103039,
          |      "periodKeyDescription": "CGT Annual 2019/2020"
          |    },
          |    {
          |      "sapDocumentNumberItem": "0001",
          |      "contractObjectType": "CGTP",
          |      "originalAmount": 12813,
          |      "contractObject": "00000250000000000259",
          |      "businessPartner": "0100276998",
          |      "mainTransaction": "5480",
          |      "taxPeriodTo": "2020-04-05",
          |      "periodKey": "19CY",
          |      "items": [
          |        {
          |          "subItem": "000",
          |          "dueDate": "2020-02-04",
          |          "amount": 12813
          |        }
          |      ],
          |      "subTransaction": "1060",
          |      "mainType": "CGT PPD Return Non UK Resident",
          |      "chargeReference": "XD002610151722",
          |      "contractAccount": "000016001259",
          |      "chargeType": "CGT PPD Return Non UK Resident",
          |      "taxPeriodFrom": "2019-04-06",
          |      "sapDocumentNumber": "003350004262",
          |      "contractAccountCategory": "16",
          |      "outstandingAmount": 12813,
          |      "periodKeyDescription": "CGT Annual 2019/2020"
          |    },
          |    {
          |      "sapDocumentNumberItem": "0001",
          |      "contractObjectType": "CGTP",
          |      "originalAmount": 100,
          |      "contractObject": "00000250000000000259",
          |      "businessPartner": "0100276998",
          |      "mainTransaction": "5510",
          |      "taxPeriodTo": "2020-04-05",
          |      "periodKey": "19CY",
          |      "items": [
          |        {
          |          "subItem": "000",
          |          "dueDate": "2020-04-01",
          |          "amount": 100
          |        }
          |      ],
          |      "subTransaction": "1080",
          |      "mainType": "CGT PPD Late Filing penalty",
          |      "chargeReference": "XH002610151822",
          |      "contractAccount": "000016001259",
          |      "chargeType": "CGT PPD Late Filing Penalty",
          |      "taxPeriodFrom": "2019-04-06",
          |      "sapDocumentNumber": "003460004233",
          |      "contractAccountCategory": "16",
          |      "outstandingAmount": 100,
          |      "periodKeyDescription": "CGT Annual 2019/2020"
          |    }
          |  ]
          |}
          |""".stripMargin
      )

      val desReturnSummaries = desListReturnResponseBody
        .validate[DesListReturnsResponse]
        .getOrElse(sys.error("Could not parse des list returns response"))

      val desFinancialData = desFinancialDataResponse
        .validate[DesFinancialDataResponse]
        .getOrElse(sys.error("Could not parse des financial data response"))

      val cgtReference       = sample[CgtReference]
      val (fromDate, toDate) = LocalDate.now().minusDays(1L) -> LocalDate.now()

      "return an error " when {

        "the http call to get the list of returns fails" in {
          mockListReturn(cgtReference, fromDate, toDate)(Left(Error("")))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the http call to get the list of returns returns with a status which is not 200" in {
          mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(404)))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the response body when getting the list of returns cannot be parsed" in {
          mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(JsString("Hi!")))))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the call to get financial data fails" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desListReturnResponseBody))))
            mockGetFinancialData(cgtReference, fromDate, toDate)(Left(Error("")))
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the http call to get financial data returns with a status which is not 200" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desListReturnResponseBody))))
            mockGetFinancialData(cgtReference, fromDate, toDate)(Right(HttpResponse(400)))
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the response body when getting financial data cannot be parsed" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desListReturnResponseBody))))
            mockGetFinancialData(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(JsNumber(1)))))
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the data cannot be transformed" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desListReturnResponseBody))))
            mockGetFinancialData(cgtReference, fromDate, toDate)(
              Right(HttpResponse(200, Some(desFinancialDataResponse)))
            )
            mockTransformReturnsList(desReturnSummaries.returnList, desFinancialData.financialTransactions)(
              Left(Error(""))
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

      }

      "return a list of returns" when {

        "the response body can be parsed and converted" in {
          val summaries = List(sample[ReturnSummary])

          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desListReturnResponseBody))))
            mockGetFinancialData(cgtReference, fromDate, toDate)(
              Right(HttpResponse(200, Some(desFinancialDataResponse)))
            )
            mockTransformReturnsList(desReturnSummaries.returnList, desFinancialData.financialTransactions)(
              Right(summaries)
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(summaries)
        }

      }

      "return an empty list of returns" when {

        "the response to list returns comes back with status 404 and a single error in the body" in {
          mockListReturn(cgtReference, fromDate, toDate)(
            Right(
              HttpResponse(
                404,
                Some(Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
                      |}
                      |""".stripMargin))
              )
            )
          )

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)

        }

        "the response to list returns comes back with status 404 and multiple errors in the body" in {
          mockListReturn(cgtReference, fromDate, toDate)(
            Right(
              HttpResponse(
                404,
                Some(Json.parse("""
                                  |{
                                  |  "failures" : [ 
                                  |    {
                                  |      "code" : "NOT_FOUND",
                                  |      "reason" : "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
                                  |    }
                                  |  ]
                                  |}  
                                  |""".stripMargin))
              )
            )
          )

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)
        }

        "the response to get financial data comes back with status 404 and a single error in the body" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desListReturnResponseBody))))
            mockGetFinancialData(cgtReference, fromDate, toDate)(
              Right(
                HttpResponse(
                  404,
                  Some(Json.parse("""
                                    |{
                                    |  "code" : "NOT_FOUND",
                                    |  "reason" : "The remote endpoint has indicated that no data can be found."
                                    |}
                                    |""".stripMargin))
                )
              )
            )
            mockTransformReturnsList(desReturnSummaries.returnList, List.empty)(
              Right(List.empty)
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)

        }

        "the response to get financial data comes back with status 404 and multiple errors in the body" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desListReturnResponseBody))))
            mockGetFinancialData(cgtReference, fromDate, toDate)(
              Right(
                HttpResponse(
                  404,
                  Some(Json.parse("""
                                    |{
                                    |  "failures" : [ 
                                    |    {
                                    |      "code" : "NOT_FOUND",
                                    |      "reason" : "The remote endpoint has indicated that no data can be found."
                                    |    }
                                    |  ]
                                    |}  
                                    |""".stripMargin))
                )
              )
            )
            mockTransformReturnsList(desReturnSummaries.returnList, List.empty)(
              Right(List.empty)
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)
        }

      }

    }

    "handling requests to display a return" must {

      val desResponseBodyString = Json.parse(
        s"""
           |{
           |	"returnType": {
           |		"source": "Agent",
           |		"submissionType": "New",
           |		"submissionDate": "2009-08-13"
           |	},
           |	"returnDetails": {
           |		"customerType": "individual",
           |		"completionDate": "2009-09-13",
           |		"isUKResident": true,
           |		"countryResidence": "FR",
           |		"numberDisposals": 1,
           |		"totalTaxableGain": 12345678912.12,
           |		"totalNetLoss": 12345678913.12,
           |		"valueAtTaxBandDetails": [
           |			{
           |				"taxRate": 18.02,
           |				"valueAtTaxRate": 12345678914.12
           |			},
           |			{
           |				"taxRate": 19.02,
           |				"valueAtTaxRate": 12345678915.12
           |			}
           |		],
           |		"totalLiability": 12345678916.12,
           |		"adjustedAmount": 12345678917.12,
           |		"totalYTDLiability": 12345678918.12,
           |		"entrepreneursRelief": 12345678919.12,
           |		"estimate": true,
           |		"repayment": true,
           |		"attachmentUpload": true,
           |		"attachmentID": "123456789",
           |		"declaration": true
           |	},
           |	"representedPersonDetails": {
           |		"capacitorPersonalRep": "Personal Representative",
           |		"firstName": "John",
           |		"lastName": "Matt",
           |		"idType": "NINO",
           |		"idValue": "SZ1235797",
           |		"dateOfDeath": "2015-08-13",
           |		"trustCessationDate": "2016-03-13",
           |		"trustTerminationDate": "2015-07-13",
           |		"addressDetails": {
           |			"addressLine1": "addressLine1",
           |			"addressLine2": "addressLine2",
           |			"addressLine3": "addressLine3",
           |			"addressLine4": "addressLine4",
           |			"countryCode": "GB",
           |			"postalCode": "TF34ER"
           |		},
           |		"email": "abc@email.com"
           |	},
           |	"disposalDetails": [		
           |		{
           |			"disposalDate": "2016-03-13",
           |			"addressDetails": {
           |				"addressLine1": "DisAddressLine11",
           |				"addressLine2": "DisAddressLine22",
           |				"addressLine3": "DisAddressLine33",
           |				"addressLine4": "DisAddressLine43",
           |				"countryCode": "GB",
           |				"postalCode": "TF34NT"
           |			},
           |			"assetType": "residential",
           |			"percentOwned": 99.00,
           |			"acquisitionType": "bought",
           |			"acquiredDate": "2017-06-13",
           |			"landRegistry": true,
           |			"acquisitionPrice": 12345678916.13,
           |			"rebased": true,
           |			"rebasedAmount": 12345678917.14,
           |			"disposalType": "sold",
           |			"disposalPrice": 12345678918.15,
           |			"improvements": true,
           |			"improvementCosts": 12345678919.16,
           |			"acquisitionFees": 12345678920.17,
           |			"disposalFees": 12345678921.18,
           |			"initialGain": 12345678922.19,
           |			"initialLoss": 12345678923.20
           |		}
           |	],
           |	"lossSummaryDetails": {
           |		"inYearLoss": true,
           |		"inYearLossUsed": 12345678923.12,
           |		"preYearLoss": true,
           |		"preYearLossUsed": 12345678925.12
           |	},
           |	"incomeAllowanceDetails": {
           |		"estimatedIncome": 12345678926.12,
           |		"personalAllowance": 12345678927.12,
           |		"annualExemption": 12345678928.12,
           |		"threshold": 12345678929.12
           |	},
           |	"reliefDetails": {
           |		"reliefs": true,
           |		"privateResRelief": 12345678935.12,
           |		"lettingsRelief": 12345678934.12,
           |		"giftHoldOverRelief": 12345678933.12,
           |		"otherRelief": "Tax Relief",
           |		"otherReliefAmount": 12345678932.12
           |	}
           |}
           |""".stripMargin
      )
      val desReturnDetails = desResponseBodyString
        .validate[DesReturnDetails]
        .getOrElse(sys.error("Could not parse json as DesReturnDetails"))

      val cgtReference = sample[CgtReference]
      val submissionId = "id"

      "return an error " when {

        "the http call fails" in {
          mockDisplayReturn(cgtReference, submissionId)(Left(Error("")))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

        "the http call returns with a status which is not 200" in {
          mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(500)))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

        "there is no response body" in {
          mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(200)))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

        "there is an error transforming the des return" in {
          inSequence {
            mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(200, Some(desResponseBodyString))))
            mockTransformReturn(desReturnDetails)(Left(Error("")))
          }

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

      }

      "return a list of returns" when {

        "the response body can be parsed and converted" in {
          val completeReturn = sample[CompleteReturn]

          inSequence {
            mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(200, Some(desResponseBodyString))))
            mockTransformReturn(desReturnDetails)(Right(completeReturn))
          }

          await(returnsService.displayReturn(cgtReference, submissionId).value) shouldBe Right(completeReturn)
        }

      }

    }

  }
}
