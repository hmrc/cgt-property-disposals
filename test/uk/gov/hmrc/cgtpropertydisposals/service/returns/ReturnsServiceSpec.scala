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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.account.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.ReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{DesReturnDetails, DesSubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{DesFinancialDataResponse, DesFinancialTransaction}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{AgentReferenceNumber, CgtReference}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse.{DeltaCharge, ReturnCharge}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.CompleteSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.audit.{SubmitReturnEvent, SubmitReturnResponseEvent}
import uk.gov.hmrc.cgtpropertydisposals.service.audit.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.email.EmailService
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.{DesListReturnsResponse, DesReturnSummary}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.transformers.{ReturnSummaryListTransformerService, ReturnTransformerService}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsServiceSpec extends AnyWordSpec with Matchers with MockFactory {

  val returnsConnector = mock[ReturnsConnector]

  val mockFinancialDataConnector = mock[FinancialDataConnector]

  val mockReturnTransformerService = mock[ReturnTransformerService]

  val mockReturnListSummaryTransformerService = mock[ReturnSummaryListTransformerService]

  val mockAuditService = mock[AuditService]

  val mockEmailService = mock[EmailService]

  val mockDraftReturnService = mock[DraftReturnsService]

  val mockAmendReturnService = mock[AmendReturnsService]

  val mockTaxYearService = mock[TaxYearService]

  val returnsService =
    new DefaultReturnsService(
      returnsConnector,
      mockFinancialDataConnector,
      mockReturnTransformerService,
      mockReturnListSummaryTransformerService,
      mockDraftReturnService,
      mockAmendReturnService,
      mockEmailService,
      mockAuditService,
      mockTaxYearService,
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

  def mockGetAvailableTaxYears = mockTaxYearService.getAvailableTaxYears _ expects () returning List(2020, 2021, 2022)

  def mockTransformReturn(desReturn: DesReturnDetails)(result: Either[Error, DisplayReturn]) =
    (mockReturnTransformerService
      .toCompleteReturn(_: DesReturnDetails))
      .expects(desReturn)
      .returning(result)

  def mockTransformReturnsList(
    returns: List[DesReturnSummary],
    financialData: List[DesFinancialTransaction],
    submitReturnRequest: List[SubmitReturnRequest]
  )(
    result: Either[Error, List[ReturnSummary]]
  ) =
    (mockReturnListSummaryTransformerService
      .toReturnSummaryList(_: List[DesReturnSummary], _: List[DesFinancialTransaction], _: List[SubmitReturnRequest]))
      .expects(returns, financialData, submitReturnRequest)
      .returning(result)

  def mockGetAmendReturnList(
    cgtReference: CgtReference
  )(result: Either[Error, List[SubmitReturnWrapper]]) =
    (mockAmendReturnService
      .getAmendedReturn(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT.fromEither[Future](result))

  def mockSaveAmendReturnList(
    submitReturnRequest: SubmitReturnRequest
  )(result: Either[Error, Unit]) =
    (mockAmendReturnService
      .saveAmendedReturn(_: SubmitReturnRequest))
      .expects(submitReturnRequest)
      .returning(EitherT.fromEither[Future](result))

  def mockSendReturnSubmitConfirmationEmail(
    submitReturnRequest: SubmitReturnRequest,
    submitReturnResponse: SubmitReturnResponse
  )(
    response: Either[Error, Unit]
  ) =
    (mockEmailService
      .sendReturnConfirmationEmail(_: SubmitReturnRequest, _: SubmitReturnResponse)(_: HeaderCarrier, _: Request[_]))
      .expects(submitReturnRequest, submitReturnResponse, *, *)
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

  def mockSaveDraftReturn(df: DraftReturn, cgtReference: CgtReference)(response: Either[Error, Unit]) =
    (mockDraftReturnService
      .saveDraftReturn(_: DraftReturn, _: CgtReference))
      .expects(df, cgtReference)
      .returning(EitherT.fromEither[Future](response))

  def mockAuditSubmitReturnResponseEvent(
    httpStatus: Int,
    responseBody: Option[JsValue],
    desSubmitReturnRequest: DesSubmitReturnRequest,
    name: Either[TrustName, IndividualName],
    agentReferenceNumber: Option[AgentReferenceNumber],
    amendReturnData: Option[AmendReturnData]
  ) =
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
          responseBody.getOrElse(Json.parse("""{ "body" : "could not parse body as JSON: " }""")),
          Json.toJson(desSubmitReturnRequest),
          name.fold(_.value, n => s"${n.firstName} ${n.lastName}"),
          agentReferenceNumber.map(_.value),
          amendReturnData.map(a => Json.toJson(a.originalReturn.completeReturn))
        ),
        "submit-return-response",
        *,
        *,
        *
      )
      .returning(())

  def mockGetDraftReturns(cgtReference: CgtReference)(response: Either[Error, List[DraftReturn]]) =
    (mockDraftReturnService
      .getDraftReturn(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT.fromEither[Future](response))

  private val emptyJsonBody = "{}"
  private val noJsonInBody  = ""

  "CompleteReturnsService" when {

    "handling submitting returns" should {

      "handle successful submits" when {

        "there is a positive charge" in {

          val formBundleId    = "804123737752"
          val chargeReference = "XCRG9448959757"

          val submitReturnResponse = SubmitReturnResponse(
            "804123737752",
            LocalDateTime.of(
              LocalDate.of(2020, 2, 20),
              LocalTime.of(9, 30, 47)
            ),
            Some(
              ReturnCharge(
                chargeReference,
                AmountInPence(1100L),
                LocalDate.of(2020, 3, 11)
              )
            ),
            None
          )

          val submitReturnRequest    = sample[SubmitReturnRequest].copy(
            amendReturnData = None
          )
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

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
                 |     "cgtReferenceNumber":"${submitReturnRequest.subscribedDetails.cgtReference}"
                 |  }
                 |}
                 |""".stripMargin)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
              Right(HttpResponse(ACCEPTED, emptyJsonBody))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)
        }

        "there is a negative charge" in {
          val formBundleId     = "804123737752"
          val chargeReference  = "XCRG9448959757"
          val responseJsonBody =
            Json.parse(s"""
                 |{
                 |"processingDate":"2020-02-20T09:30:47Z",
                 |"ppdReturnResponseDetails": {
                 |     "chargeType": "Late Penalty",
                 |     "chargeReference":"$chargeReference",
                 |     "amount":-11.0,
                 |     "dueDate":"2020-03-11",
                 |     "formBundleNumber":"$formBundleId",
                 |     "cgtReferenceNumber":"XLCGTP212487578"
                 |  }
                 |}
                 |""".stripMargin)

          val submitReturnRequest = sample[SubmitReturnRequest].copy(amendReturnData = None)

          val submitReturnResponse = SubmitReturnResponse(
            formBundleId,
            LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47)),
            None,
            None
          )

          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)
          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
              Right(HttpResponse(ACCEPTED, emptyJsonBody))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)
        }

        "there is a no charge data" in {
          val formBundleId     = "804123737752"
          val processingDate   = LocalDateTime.of(
            LocalDate.of(2020, 2, 20),
            LocalTime.of(9, 30, 47)
          )
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

          val submitReturnResponse   = SubmitReturnResponse(formBundleId, processingDate, None, None)
          val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData = None)
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)
          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
              Right(HttpResponse(ACCEPTED, emptyJsonBody))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)
        }

        "there is a zero charge" in {
          val formBundleId     = "804123737752"
          val processingDate   = LocalDateTime.of(
            LocalDate.of(2020, 2, 20),
            LocalTime.of(9, 30, 47)
          )
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

          val submitReturnResponse   = SubmitReturnResponse(formBundleId, processingDate, None, None)
          val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData = None)
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
              Right(HttpResponse(ACCEPTED, emptyJsonBody))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)
        }

        "there is a positive charge and email call returns 500 and the EmailSent event won't be sent" in {
          val formBundleId           = "804123737752"
          val chargeReference        = "XCRG9448959757"
          val responseJsonBody       =
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
          val submitReturnResponse   = SubmitReturnResponse(
            "804123737752",
            LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47)),
            Some(
              ReturnCharge(
                chargeReference,
                AmountInPence(1100L),
                LocalDate.of(2020, 3, 11)
              )
            ),
            None
          )
          val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData = None)
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
              Right(HttpResponse(500, emptyJsonBody))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)
        }

      }

      "handling submitted amend returns" must {

        def testGetAndModifyDraftReturns[D <: DraftReturn](draftReturn: D)(modifyDraftReturn: Option[D => D]): Unit = {
          val formBundleId           = "formBundleId"
          val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData =
            Some(
              sample[AmendReturnData].copy(
                originalReturn = sample[CompleteReturnWithSummary].copy(
                  summary = sample[ReturnSummary].copy(
                    submissionId = formBundleId,
                    expired = false
                  )
                )
              )
            )
          )
          val cgtReference           = submitReturnRequest.subscribedDetails.cgtReference
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)
          val responseJsonBody       =
            Json.parse(s"""
                 |{
                 |"processingDate":"2020-02-20T09:30:47Z",
                 |"ppdReturnResponseDetails": {
                 |     "formBundleNumber":"$formBundleId",
                 |     "cgtReferenceNumber":"${cgtReference.value}"
                 |  }
                 |}
                 |""".stripMargin)
          val submitReturnResponse   = SubmitReturnResponse(
            formBundleId,
            LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47)),
            None,
            None
          )

          inSequence {
            mockAuditSubmitReturnEvent(
              cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
              Right(HttpResponse(ACCEPTED, emptyJsonBody))
            )
            mockGetDraftReturns(cgtReference)(Right(List(draftReturn)))
            modifyDraftReturn.foreach(modify => mockSaveDraftReturn(modify(draftReturn), cgtReference)(Right(())))
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)
        }

        "delete section 3 of any draft returns when any of those questions have been answered in them" when {

          "there is a single disposal draft return" in {
            testGetAndModifyDraftReturns(
              sample[DraftSingleDisposalReturn].copy(
                exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]),
                yearToDateLiabilityAnswers = Some(sample[CompleteNonCalculatedYTDAnswers]),
                supportingEvidenceAnswers = Some(sample[CompleteSupportingEvidenceAnswers])
              )
            )(
              Some(
                _.copy(
                  exemptionAndLossesAnswers = None,
                  yearToDateLiabilityAnswers = None,
                  supportingEvidenceAnswers = None
                )
              )
            )
          }

          "there is a multiple disposals draft return" in {
            testGetAndModifyDraftReturns(
              sample[DraftMultipleDisposalsReturn].copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = Some(sample[CompleteNonCalculatedYTDAnswers]),
                supportingEvidenceAnswers = Some(sample[CompleteSupportingEvidenceAnswers])
              )
            )(
              Some(
                _.copy(
                  yearToDateLiabilityAnswers = None,
                  supportingEvidenceAnswers = None
                )
              )
            )
          }

          "there is a single indirect disposal draft return" in {
            testGetAndModifyDraftReturns(
              sample[DraftSingleIndirectDisposalReturn].copy(
                exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]),
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = Some(sample[CompleteSupportingEvidenceAnswers])
              )
            )(
              Some(
                _.copy(
                  exemptionAndLossesAnswers = None,
                  supportingEvidenceAnswers = None
                )
              )
            )
          }

          "there is a multiple indirect disposals  draft return" in {
            testGetAndModifyDraftReturns(
              sample[DraftSingleDisposalReturn].copy(
                exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]),
                yearToDateLiabilityAnswers = Some(sample[CompleteNonCalculatedYTDAnswers]),
                supportingEvidenceAnswers = None
              )
            )(
              Some(
                _.copy(
                  exemptionAndLossesAnswers = None,
                  yearToDateLiabilityAnswers = None
                )
              )
            )
          }

          "there is a single mixed use disposal draft return" in {
            testGetAndModifyDraftReturns(
              sample[DraftSingleMixedUseDisposalReturn].copy(
                exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]),
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )(
              Some(
                _.copy(
                  exemptionAndLossesAnswers = None
                )
              )
            )
          }

        }

        "not modify any draft returns" when {

          "they do not have any of section 3 filled in" in {
            testGetAndModifyDraftReturns(
              sample[DraftSingleDisposalReturn].copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )(
              None
            )
          }

        }

      }

      "return an error" when {

        "there are charge details for a non zero charge amount and " when {

          def test(jsonResponseBody: JsValue): Unit = {
            val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData = None)
            val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

            inSequence {
              mockAuditSubmitReturnEvent(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest,
                submitReturnRequest.agentReferenceNumber
              )
              mockSubmitReturn(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest
              )(Right(HttpResponse(200, jsonResponseBody, Map.empty[String, Seq[String]])))
              mockAuditSubmitReturnResponseEvent(
                200,
                Some(jsonResponseBody),
                desSubmitReturnRequest,
                submitReturnRequest.subscribedDetails.name,
                submitReturnRequest.agentReferenceNumber,
                submitReturnRequest.amendReturnData
              )
              mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            }

            await(returnsService.submitReturn(submitReturnRequest, None).value).isLeft shouldBe true
          }

          def test2(jsonResponseBody: JsValue): Unit = {
            val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData = None)
            val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

            inSequence {
              mockAuditSubmitReturnEvent(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest,
                submitReturnRequest.agentReferenceNumber
              )
              mockSubmitReturn(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest
              )(Right(HttpResponse(200, jsonResponseBody, Map.empty[String, Seq[String]])))
              mockAuditSubmitReturnResponseEvent(
                200,
                Some(jsonResponseBody),
                desSubmitReturnRequest,
                submitReturnRequest.subscribedDetails.name,
                submitReturnRequest.agentReferenceNumber,
                submitReturnRequest.amendReturnData
              )
              mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            }

            await(returnsService.submitReturn(submitReturnRequest, None).value).isLeft shouldBe true
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
            test2(
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
          val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData =
            Some(
              sample[AmendReturnData].copy(
                originalReturn = sample[CompleteReturnWithSummary].copy(
                  summary = sample[ReturnSummary].copy(expired = false)
                )
              )
            )
          )
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

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
          await(returnsService.submitReturn(submitReturnRequest, None).value).isLeft shouldBe true
        }

        "the http call comes back with a status other than 200" in {
          val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData =
            Some(
              sample[AmendReturnData].copy(
                originalReturn = sample[CompleteReturnWithSummary].copy(
                  summary = sample[ReturnSummary].copy(expired = false)
                )
              )
            )
          )
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(500, noJsonInBody)))
            mockAuditSubmitReturnResponseEvent(
              500,
              None,
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
          }
          await(returnsService.submitReturn(submitReturnRequest, None).value).isLeft shouldBe true
        }

        "there is an error updating a draft return for an amended return" in {
          val formBundleId           = "formBundleId"
          val submitReturnRequest    = sample[SubmitReturnRequest].copy(amendReturnData =
            Some(
              sample[AmendReturnData].copy(
                originalReturn = sample[CompleteReturnWithSummary].copy(
                  summary = sample[ReturnSummary].copy(
                    submissionId = formBundleId,
                    expired = false
                  )
                )
              )
            )
          )
          val cgtReference           = submitReturnRequest.subscribedDetails.cgtReference
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)
          val responseJsonBody       =
            Json.parse(s"""
                 |{
                 |"processingDate":"2020-02-20T09:30:47Z",
                 |"ppdReturnResponseDetails": {
                 |     "formBundleNumber":"$formBundleId",
                 |     "cgtReferenceNumber":"${cgtReference.value}"
                 |  }
                 |}
                 |""".stripMargin)
          val submitReturnResponse   = SubmitReturnResponse(
            formBundleId,
            LocalDateTime.of(LocalDate.of(2020, 2, 20), LocalTime.of(9, 30, 47)),
            None,
            None
          )

          val draftReturn1 = sample[DraftSingleDisposalReturn]
            .copy(exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]))
          val draftReturn2 = sample[DraftSingleDisposalReturn]
            .copy(exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]))
          val draftReturn3 = sample[DraftSingleDisposalReturn]
            .copy(exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]))

          inSequence {
            mockAuditSubmitReturnEvent(
              cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
              Right(HttpResponse(ACCEPTED, emptyJsonBody))
            )
            mockGetDraftReturns(cgtReference)(Right(List(draftReturn1, draftReturn2, draftReturn3)))
            mockSaveDraftReturn(
              draftReturn3.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              ),
              cgtReference
            )(Right(()))
            mockSaveDraftReturn(
              draftReturn2.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              ),
              cgtReference
            )(Left(Error("")))
            mockSaveDraftReturn(
              draftReturn1.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              ),
              cgtReference
            )(Right(()))
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value).isLeft shouldBe true

        }

      }

      "return a delta charge error" when {

        "there are no DesFinancialTransaction which matches return's charge reference" in {

          val formBundleId    = "804123737752"
          val chargeReference = "XCRG9448959757"

          val submitReturnRequest    = sample[SubmitReturnRequest].copy(
            amendReturnData = Some(
              sample[AmendReturnData].copy(
                originalReturn = sample[CompleteReturnWithSummary].copy(
                  summary = sample[ReturnSummary].copy(
                    submissionId = formBundleId,
                    expired = false
                  )
                )
              )
            )
          )
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

          val desFinancialDataResponse = Json.parse(
            s"""
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
               |          "amount": 12812
               |        }
               |      ],
               |      "subTransaction": "1060",
               |      "mainType": "CGT PPD Return UK Resident",
               |      "chargeReference": "XCRG9448222777",
               |      "contractAccount": "000016001259",
               |      "chargeType": "CGT PPD Return UK Resident",
               |      "taxPeriodFrom": "2019-04-06",
               |      "sapDocumentNumber": "003070004278",
               |      "contractAccountCategory": "16",
               |      "outstandingAmount": 12812,
               |      "periodKeyDescription": "CGT Annual 2019/2020"
               |    }
               |  ]
               |}
               |""".stripMargin
          )
          val financialTransactions    = desFinancialDataResponse.as[DesFinancialDataResponse].financialTransactions
          val taxYear                  = submitReturnRequest.completeReturn.fold(
            _.triageAnswers.taxYear,
            _.triageAnswers.disposalDate.taxYear,
            _.triageAnswers.disposalDate.taxYear,
            _.triageAnswers.taxYear,
            _.triageAnswers.disposalDate.taxYear
          )
          val (fromDate, toDate)       = (taxYear.startDateInclusive, taxYear.endDateExclusive)

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
                 |     "cgtReferenceNumber":"${submitReturnRequest.subscribedDetails.cgtReference}"
                 |  }
                 |}
                 |""".stripMargin)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockGetFinancialData(submitReturnRequest.subscribedDetails.cgtReference, fromDate, toDate)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Left(
            Error(
              s"Could not find one or two transactions to look for delta charge  with charge reference $chargeReference: $financialTransactions"
            )
          )
        }

        "there is more than two DesFinancialTransactions which matches return's charge reference" in {

          val formBundleId    = "804123737752"
          val chargeReference = "XCRG9448959757"

          val submitReturnRequest    = sample[SubmitReturnRequest].copy(
            amendReturnData = Some(
              sample[AmendReturnData].copy(
                originalReturn = sample[CompleteReturnWithSummary].copy(
                  summary = sample[ReturnSummary].copy(
                    submissionId = formBundleId,
                    expired = false
                  )
                )
              )
            )
          )
          val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

          val desFinancialDataResponse = Json.parse(
            s"""
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
               |      "chargeReference": "$chargeReference",
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
               |      "chargeReference": "$chargeReference",
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
               |      "chargeReference": "$chargeReference",
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
               |      "chargeReference": "$chargeReference",
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

          val financialTransactions = desFinancialDataResponse.as[DesFinancialDataResponse].financialTransactions
          val taxYear               = submitReturnRequest.completeReturn.fold(
            _.triageAnswers.taxYear,
            _.triageAnswers.disposalDate.taxYear,
            _.triageAnswers.disposalDate.taxYear,
            _.triageAnswers.taxYear,
            _.triageAnswers.disposalDate.taxYear
          )
          val (fromDate, toDate)    = (taxYear.startDateInclusive, taxYear.endDateExclusive)
          val responseJsonBody      =
            Json.parse(s"""
                 |{
                 |"processingDate":"2020-02-20T09:30:47Z",
                 |"ppdReturnResponseDetails": {
                 |     "chargeType": "Late Penalty",
                 |     "chargeReference":"$chargeReference",
                 |     "amount":11.0,
                 |     "dueDate":"2020-03-11",
                 |     "formBundleNumber":"$formBundleId",
                 |     "cgtReferenceNumber":"${submitReturnRequest.subscribedDetails.cgtReference}"
                 |  }
                 |}
                 |""".stripMargin)

          inSequence {
            mockAuditSubmitReturnEvent(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest,
              submitReturnRequest.agentReferenceNumber
            )
            mockSubmitReturn(
              submitReturnRequest.subscribedDetails.cgtReference,
              desSubmitReturnRequest
            )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
            mockAuditSubmitReturnResponseEvent(
              200,
              Some(responseJsonBody),
              desSubmitReturnRequest,
              submitReturnRequest.subscribedDetails.name,
              submitReturnRequest.agentReferenceNumber,
              submitReturnRequest.amendReturnData
            )
            mockSaveAmendReturnList(submitReturnRequest)(Right(()))
            mockGetFinancialData(submitReturnRequest.subscribedDetails.cgtReference, fromDate, toDate)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
          }

          await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Left(
            Error(
              s"Could not find one or two transactions to look for delta charge  with charge reference $chargeReference: $financialTransactions"
            )
          )
        }

      }

      "return expected delta charge" when {

        "there are only one DesFinancialTransaction which matches return's charge reference" in {
          def test[D <: DraftReturn](draftReturn: D)(modifyDraftReturn: Option[D => D]): Unit = {

            val formBundleId    = "804123737752"
            val chargeReference = "XCRG9448959757"

            val submitReturnRequest    = sample[SubmitReturnRequest].copy(
              amendReturnData = Some(
                sample[AmendReturnData].copy(
                  originalReturn = sample[CompleteReturnWithSummary].copy(
                    summary = sample[ReturnSummary].copy(
                      submissionId = formBundleId,
                      expired = false
                    )
                  )
                )
              )
            )
            val cgtReference           = submitReturnRequest.subscribedDetails.cgtReference
            val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

            val desFinancialDataResponse = Json.parse(
              s"""
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
                 |          "amount": 12812
                 |        }
                 |      ],
                 |      "subTransaction": "1060",
                 |      "mainType": "CGT PPD Return UK Resident",
                 |      "chargeReference": "$chargeReference",
                 |      "contractAccount": "000016001259",
                 |      "chargeType": "CGT PPD Return UK Resident",
                 |      "taxPeriodFrom": "2019-04-06",
                 |      "sapDocumentNumber": "003070004278",
                 |      "contractAccountCategory": "16",
                 |      "outstandingAmount": 12812,
                 |      "periodKeyDescription": "CGT Annual 2019/2020"
                 |    }
                 |  ]
                 |}
                 |""".stripMargin
            )

            val taxYear              = submitReturnRequest.completeReturn.fold(
              _.triageAnswers.taxYear,
              _.triageAnswers.disposalDate.taxYear,
              _.triageAnswers.disposalDate.taxYear,
              _.triageAnswers.taxYear,
              _.triageAnswers.disposalDate.taxYear
            )
            val submitReturnResponse = SubmitReturnResponse(
              "804123737752",
              LocalDateTime.of(
                LocalDate.of(2020, 2, 20),
                LocalTime.of(9, 30, 47)
              ),
              Some(
                ReturnCharge(
                  chargeReference,
                  AmountInPence(1100L),
                  LocalDate.of(2020, 3, 11)
                )
              ),
              None
            )
            val (fromDate, toDate)   = (taxYear.startDateInclusive, taxYear.endDateExclusive)
            val responseJsonBody     =
              Json.parse(s"""
                   |{
                   |"processingDate":"2020-02-20T09:30:47Z",
                   |"ppdReturnResponseDetails": {
                   |     "chargeType": "Late Penalty",
                   |     "chargeReference":"$chargeReference",
                   |     "amount":11.0,
                   |     "dueDate":"2020-03-11",
                   |     "formBundleNumber":"$formBundleId",
                   |     "cgtReferenceNumber":"${submitReturnRequest.subscribedDetails.cgtReference}"
                   |  }
                   |}
                   |""".stripMargin)

            inSequence {
              mockAuditSubmitReturnEvent(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest,
                submitReturnRequest.agentReferenceNumber
              )
              mockSubmitReturn(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest
              )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
              mockAuditSubmitReturnResponseEvent(
                200,
                Some(responseJsonBody),
                desSubmitReturnRequest,
                submitReturnRequest.subscribedDetails.name,
                submitReturnRequest.agentReferenceNumber,
                submitReturnRequest.amendReturnData
              )
              mockSaveAmendReturnList(submitReturnRequest)(Right(()))
              mockGetFinancialData(submitReturnRequest.subscribedDetails.cgtReference, fromDate, toDate)(
                Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
              )
              mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
                Right(HttpResponse(ACCEPTED, emptyJsonBody))
              )
              mockGetDraftReturns(cgtReference)(Right(List(draftReturn)))
              modifyDraftReturn.foreach(modify => mockSaveDraftReturn(modify(draftReturn), cgtReference)(Right(())))
            }

            await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)

          }

          test(
            sample[DraftSingleDisposalReturn].copy(
              exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]),
              yearToDateLiabilityAnswers = Some(sample[CompleteNonCalculatedYTDAnswers]),
              supportingEvidenceAnswers = Some(sample[CompleteSupportingEvidenceAnswers])
            )
          )(
            Some(
              _.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )
          )
        }

        "there are two DesFinancialTransactions which matches return's charge reference" in {
          def test[D <: DraftReturn](draftReturn: D)(modifyDraftReturn: Option[D => D]): Unit = {
            val formBundleId    = "804123737752"
            val chargeReference = "XCRG9448959757"

            val submitReturnRequest    = sample[SubmitReturnRequest].copy(
              amendReturnData = Some(
                sample[AmendReturnData].copy(
                  originalReturn = sample[CompleteReturnWithSummary].copy(
                    summary = sample[ReturnSummary].copy(
                      submissionId = formBundleId,
                      expired = false
                    )
                  )
                )
              )
            )
            val cgtReference           = submitReturnRequest.subscribedDetails.cgtReference
            val desSubmitReturnRequest = DesSubmitReturnRequest(submitReturnRequest, None)

            val desFinancialDataResponse = Json.parse(
              s"""
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
                 |          "amount": 12812
                 |        }
                 |      ],
                 |      "subTransaction": "1060",
                 |      "mainType": "CGT PPD Return UK Resident",
                 |      "chargeReference": "$chargeReference",
                 |      "contractAccount": "000016001259",
                 |      "chargeType": "CGT PPD Return UK Resident",
                 |      "taxPeriodFrom": "2019-04-06",
                 |      "sapDocumentNumber": "003070004278",
                 |      "contractAccountCategory": "16",
                 |      "outstandingAmount": 12812,
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
                 |      "chargeReference": "$chargeReference",
                 |      "contractAccount": "000016001259",
                 |      "chargeType": "CGT PPD Return UK Resident",
                 |      "taxPeriodFrom": "2019-04-06",
                 |      "sapDocumentNumber": "003100004253",
                 |      "contractAccountCategory": "16",
                 |      "outstandingAmount": 103039,
                 |      "periodKeyDescription": "CGT Annual 2019/2020"
                 |    }
                 |  ]
                 |}
                 |""".stripMargin
            )

            val taxYear            = submitReturnRequest.completeReturn.fold(
              _.triageAnswers.taxYear,
              _.triageAnswers.disposalDate.taxYear,
              _.triageAnswers.disposalDate.taxYear,
              _.triageAnswers.taxYear,
              _.triageAnswers.disposalDate.taxYear
            )
            val (fromDate, toDate) = (taxYear.startDateInclusive, taxYear.endDateExclusive)

            val returnCharge1 = ReturnCharge("XCRG9448959757", AmountInPence(10303900), LocalDate.of(2020, 3, 2))
            val returnCharge2 = ReturnCharge("XCRG9448959757", AmountInPence(1281200), LocalDate.of(2020, 3, 3))

            val deltaCharge          = DeltaCharge(returnCharge1, returnCharge2)
            val submitReturnResponse = SubmitReturnResponse(
              "804123737752",
              LocalDateTime.of(
                LocalDate.of(2020, 2, 20),
                LocalTime.of(9, 30, 47)
              ),
              Some(returnCharge1),
              Some(deltaCharge)
            )
            val responseJsonBody     =
              Json.parse(s"""
                   |{
                   |"processingDate":"2020-02-20T09:30:47Z",
                   |"ppdReturnResponseDetails": {
                   |     "chargeType": "Late Penalty",
                   |     "chargeReference":"$chargeReference",
                   |     "amount":103039.0,
                   |     "dueDate":"2020-03-02",
                   |     "formBundleNumber":"$formBundleId",
                   |     "cgtReferenceNumber":"${submitReturnRequest.subscribedDetails.cgtReference}"
                   |  }
                   |}
                   |""".stripMargin)

            inSequence {
              mockAuditSubmitReturnEvent(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest,
                submitReturnRequest.agentReferenceNumber
              )
              mockSubmitReturn(
                submitReturnRequest.subscribedDetails.cgtReference,
                desSubmitReturnRequest
              )(Right(HttpResponse(200, responseJsonBody, Map.empty[String, Seq[String]])))
              mockAuditSubmitReturnResponseEvent(
                200,
                Some(responseJsonBody),
                desSubmitReturnRequest,
                submitReturnRequest.subscribedDetails.name,
                submitReturnRequest.agentReferenceNumber,
                submitReturnRequest.amendReturnData
              )
              mockSaveAmendReturnList(submitReturnRequest)(Right(()))
              mockGetFinancialData(submitReturnRequest.subscribedDetails.cgtReference, fromDate, toDate)(
                Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
              )
              mockSendReturnSubmitConfirmationEmail(submitReturnRequest, submitReturnResponse)(
                Right(HttpResponse(ACCEPTED, emptyJsonBody))
              )
              mockGetDraftReturns(cgtReference)(Right(List(draftReturn)))
              modifyDraftReturn.foreach(modify => mockSaveDraftReturn(modify(draftReturn), cgtReference)(Right(())))
            }

            await(returnsService.submitReturn(submitReturnRequest, None).value) shouldBe Right(submitReturnResponse)
          }

          test(
            sample[DraftSingleDisposalReturn].copy(
              exemptionAndLossesAnswers = Some(sample[CompleteExemptionAndLossesAnswers]),
              yearToDateLiabilityAnswers = Some(sample[CompleteNonCalculatedYTDAnswers]),
              supportingEvidenceAnswers = Some(sample[CompleteSupportingEvidenceAnswers])
            )
          )(
            Some(
              _.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )
          )
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

      val (fromDate1, toDate1) = LocalDate.of(2020, 4, 6) -> LocalDate.of(2021, 4, 5)
      val (fromDate2, toDate2) = LocalDate.of(2021, 4, 6) -> LocalDate.of(2022, 4, 5)
      val (fromDate3, toDate3) = LocalDate.of(2022, 4, 6) -> LocalDate.of(2023, 4, 5)
      "return an error " when {

        "the http call to get the list of returns fails" in {
          mockListReturn(cgtReference, fromDate, toDate)(Left(Error("")))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the http call to get the list of returns returns with a status which is not 200" in {
          mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(404, emptyJsonBody)))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the response body when getting the list of returns cannot be parsed" in {
          mockListReturn(cgtReference, fromDate, toDate)(
            Right(HttpResponse(200, JsString("Hi!"), Map.empty[String, Seq[String]]))
          )

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the call to get financial data fails" in {
          inSequence {
            mockListReturn(cgtReference, fromDate1, toDate3)(
              Right(HttpResponse(200, desListReturnResponseBody, Map.empty[String, Seq[String]]))
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(Left(Error("")))
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(Left(Error("")))
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(Left(Error("")))
          }

          await(returnsService.listReturns(cgtReference, fromDate1, toDate3).value).isLeft shouldBe true
        }

        "the http call to get financial data returns with a status which is not 200" in {
          inSequence {
            mockListReturn(cgtReference, fromDate1, toDate3)(
              Right(HttpResponse(200, desListReturnResponseBody, Map.empty[String, Seq[String]]))
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(Right(HttpResponse(400, emptyJsonBody)))
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(Right(HttpResponse(400, emptyJsonBody)))
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(Right(HttpResponse(400, emptyJsonBody)))
          }

          await(returnsService.listReturns(cgtReference, fromDate1, toDate3).value).isLeft shouldBe true
        }

        "the response body when getting financial data cannot be parsed" in {
          inSequence {
            mockListReturn(cgtReference, fromDate1, toDate3)(
              Right(HttpResponse(200, desListReturnResponseBody, Map.empty[String, Seq[String]]))
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(
              Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(
              Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(
              Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]]))
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate1, toDate3).value).isLeft shouldBe true
        }

        "the data cannot be transformed" in {
          inSequence {
            mockListReturn(cgtReference, fromDate1, toDate3)(
              Right(HttpResponse(200, desListReturnResponseBody, Map.empty[String, Seq[String]]))
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that no data can be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that no data can be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetAmendReturnList(cgtReference)(Right(List.empty))
            mockTransformReturnsList(desReturnSummaries.returnList, desFinancialData.financialTransactions, List.empty)(
              Left(Error(""))
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate1, toDate3).value).isLeft shouldBe true
        }

      }

      "return a list of returns" when {

        "the response body can be parsed and converted" in {
          val summaries = List(sample[ReturnSummary])

          inSequence {
            mockListReturn(cgtReference, fromDate1, toDate3)(
              Right(HttpResponse(200, desListReturnResponseBody, Map.empty[String, Seq[String]]))
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that no data can be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that no data can be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetAmendReturnList(cgtReference)(Right(List.empty))
            mockTransformReturnsList(desReturnSummaries.returnList, desFinancialData.financialTransactions, List.empty)(
              Right(summaries)
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate1, toDate3).value) shouldBe Right(summaries)
        }

      }

      "return an empty list of returns" when {

        "the response to list returns comes back with status 404 and a single error in the body" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetAmendReturnList(cgtReference)(Right(List.empty))
          }

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)

        }

        "the response to list returns comes back with status 404 and multiple errors in the body" in {
          inSequence {
            mockListReturn(cgtReference, fromDate, toDate)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "failures" : [ 
                      |    {
                      |      "code" : "NOT_FOUND",
                      |      "reason" : "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
                      |    }
                      |  ]
                      |}  
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(
              Right(HttpResponse(200, desFinancialDataResponse, Map.empty[String, Seq[String]]))
            )
            mockGetAmendReturnList(cgtReference)(Right(List.empty))
          }
          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)
        }

        "the response to get financial data comes back with status 404 and a single error in the body" in {
          inSequence {
            mockListReturn(cgtReference, fromDate1, toDate3)(
              Right(HttpResponse(200, desListReturnResponseBody, Map.empty[String, Seq[String]]))
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that no data can be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that no data can be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "code" : "NOT_FOUND",
                      |  "reason" : "The remote endpoint has indicated that no data can be found."
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetAmendReturnList(cgtReference)(Right(List.empty))

            mockTransformReturnsList(desReturnSummaries.returnList, List.empty, List.empty)(
              Right(List.empty)
            )
          }

          await(returnsService.listReturns(cgtReference, fromDate1, toDate3).value) shouldBe Right(List.empty)

        }

        "the response to get financial data comes back with status 404 and multiple errors in the body" in {
          inSequence {
            mockListReturn(cgtReference, fromDate1, toDate3)(
              Right(HttpResponse(200, desListReturnResponseBody, Map.empty[String, Seq[String]]))
            )
            mockGetAvailableTaxYears
            mockGetFinancialData(cgtReference, fromDate1, toDate1)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "failures" : [
                      |    {
                      |      "code" : "NOT_FOUND",
                      |      "reason" : "The remote endpoint has indicated that no data can be found."
                      |    }
                      |  ]
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetFinancialData(cgtReference, fromDate2, toDate2)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "failures" : [
                      |    {
                      |      "code" : "NOT_FOUND",
                      |      "reason" : "The remote endpoint has indicated that no data can be found."
                      |    }
                      |  ]
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetFinancialData(cgtReference, fromDate3, toDate3)(
              Right(
                HttpResponse(
                  404,
                  Json.parse("""
                      |{
                      |  "failures" : [
                      |    {
                      |      "code" : "NOT_FOUND",
                      |      "reason" : "The remote endpoint has indicated that no data can be found."
                      |    }
                      |  ]
                      |}
                      |""".stripMargin),
                  Map.empty[String, Seq[String]]
                )
              )
            )
            mockGetAmendReturnList(cgtReference)(Right(List.empty))

            mockTransformReturnsList(desReturnSummaries.returnList, List.empty, List.empty)(
              Right(List.empty)
            )

          }

          await(returnsService.listReturns(cgtReference, fromDate1, toDate3).value) shouldBe Right(List.empty)
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
      val desReturnDetails      = desResponseBodyString
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
          mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(500, emptyJsonBody)))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

        "there is no response body" in {
          mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(200, emptyJsonBody)))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

        "there is an error transforming the des return" in {
          inSequence {
            mockDisplayReturn(cgtReference, submissionId)(
              Right(HttpResponse(200, desResponseBodyString, Map.empty[String, Seq[String]]))
            )
            mockTransformReturn(desReturnDetails)(Left(Error("")))
          }

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

      }

      "return a list of returns" when {

        "the response body can be parsed and converted" in {
          val displayReturn = sample[DisplayReturn]

          inSequence {
            mockDisplayReturn(cgtReference, submissionId)(
              Right(HttpResponse(200, desResponseBodyString, Map.empty[String, Seq[String]]))
            )
            mockTransformReturn(desReturnDetails)(Right(displayReturn))
            // mockGetAmendReturnList(cgtReference)(Right(List.empty))
          }

          await(returnsService.displayReturn(cgtReference, submissionId).value) shouldBe Right(displayReturn)
        }

      }

    }

  }

}
