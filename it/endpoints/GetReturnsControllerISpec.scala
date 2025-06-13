/*
 * Copyright 2025 HM Revenue & Customs
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

package endpoints

import cats.data.EitherT
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.ws.{WSRequest, WSResponse}
import stubs.{AuthStub, DownstreamStub}
import support.IntegrationBaseSpec
import uk.gov.hmrc.cgtpropertydisposals.models
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.address.Postcode
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, DesFinancialDataResponse, DesFinancialTransaction}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AmendReturnData, ListReturnsResponse, ReturnSummary, SubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.{AmendReturnsRepository, DefaultAmendReturnsRepository}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DesReturnSummary
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DesReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.ReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.SubmitReturnGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*

import uk.gov.hmrc.time.{TaxYear => HmrcTaxYear}

import java.time.LocalDate
import scala.concurrent.Future
import play.api.libs.ws.WSBodyReadables.readableAsString

class GetReturnsControllerISpec extends IntegrationBaseSpec {

  trait Test {
    val currentTaxYear: Int = HmrcTaxYear.current.startYear

    val cgtRef: String   = "dummyCgtRef"
    val fromDate: String = s"$currentTaxYear-01-01"
    val toDate: String   = s"$currentTaxYear-02-01"

    def listRouteUri: String                           = s"/returns/$cgtRef/$fromDate/$toDate"
    val listDownstreamUri: String                      = s"/capital-gains-tax/returns/$cgtRef"
    val listDownstreamQueryParams: Map[String, String] = Map("fromDate" -> fromDate, "toDate" -> toDate)

    val getFinancialDataDownstreamUri: String = s"/enterprise/financial-data/ZCGT/$cgtRef/CGT"

    def getTaxYearQueryDates(startYear: Int): Map[String, String] = Map(
      "dateFrom" -> s"$startYear-04-06",
      "dateTo"   -> s"${startYear + 1}-04-05"
    )

    val taxYearQueryDates: Seq[Map[String, String]] = (currentTaxYear - 4 to currentTaxYear).map(getTaxYearQueryDates)

    val notFoundFinancialDataErrorBody: JsObject = Json.obj(
      "code"   -> "NOT_FOUND",
      "reason" -> "The remote endpoint has indicated that no data can be found."
    )

    val financialDataResponse: JsValue = Json.obj(
      "financialTransactions" -> JsArray(Seq(Json.toJson(sample[DesFinancialTransaction])))
    )

    private def request(uri: String): WSRequest = buildRequest(uri)
      .withHttpHeaders(
        (AUTHORIZATION, "Bearer 123") // some bearer token
      )

    def listRequest: WSRequest = request(listRouteUri)
  }

  "/returns/:cgtReference/:fromDate/:toDate" when {
    "user is unauthenticated" should {
      "return 403 error response with expected body" in new Test {
        AuthStub.unauthorised()

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe FORBIDDEN
        result.body   shouldBe "Forbidden"
      }
    }

    "there are errors in the request" must {
      "[fromDate improperly formatted] return 400 error response with no body" in new Test {
        AuthStub.authorised()
        override val fromDate: String = "wrongFormat"

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe BAD_REQUEST
        result.body   shouldBe ""
      }

      "[toDate improperly formatted] return 400 error response with no body" in new Test {
        AuthStub.authorised()
        override val toDate: String = "wrongFormat"

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe BAD_REQUEST
        result.body   shouldBe ""
      }

      "[fromDate & toDate improperly formatted] return 400 error response with no body" in new Test {
        AuthStub.authorised()
        override val fromDate: String = "wrongFormat"
        override val toDate: String   = "wrongFormat"

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe BAD_REQUEST
        result.body   shouldBe ""
      }
    }

    "user is authenticated and request is properly formatted" should {
      "return a 500 error response when an unexpected error occurs while retrieving user returns" in new Test {
        AuthStub.authorised()

        DownstreamStub.onError(
          method = DownstreamStub.GET,
          uri = listDownstreamUri,
          queryParams = listDownstreamQueryParams,
          errorStatus = BAD_REQUEST,
          errorBody = ""
        )

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe INTERNAL_SERVER_ERROR
        result.body   shouldBe ""
      }

      "return a 500 error response when an unexpected error occurs while retrieving financial data" in new Test {
        AuthStub.authorised()

        DownstreamStub.onSuccess(
          method = DownstreamStub.GET,
          uri = listDownstreamUri,
          queryParams = listDownstreamQueryParams,
          status = OK,
          body = Json.obj("returnList" -> JsArray(Seq(Json.toJson(sample[DesReturnSummary]))))
        )

        taxYearQueryDates.foreach(params =>
          DownstreamStub.onError(
            method = DownstreamStub.GET,
            uri = getFinancialDataDownstreamUri,
            queryParams = params,
            errorStatus = IM_A_TEAPOT,
            errorBody = "dummyError"
          )
        )

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe INTERNAL_SERVER_ERROR
        result.body   shouldBe ""
      }

      "return a 500 error response when returns exist but financial data does not" in new Test {
        AuthStub.authorised()

        DownstreamStub.onSuccess(
          method = DownstreamStub.GET,
          uri = listDownstreamUri,
          queryParams = listDownstreamQueryParams,
          status = OK,
          body = Json.obj("returnList" -> JsArray(Seq(Json.toJson(sample[DesReturnSummary]))))
        )

        taxYearQueryDates.foreach(params =>
          DownstreamStub.onError(
            method = DownstreamStub.GET,
            uri = getFinancialDataDownstreamUri,
            queryParams = params,
            errorStatus = NOT_FOUND,
            errorBody = notFoundFinancialDataErrorBody.toString()
          )
        )

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe INTERNAL_SERVER_ERROR
        result.body   shouldBe ""
      }

      "return a 200 success with empty returns when a CGT reference is in use but no returns are found" in new Test {
        AuthStub.authorised()

        val notFoundReturnsErrorBody: JsObject = Json.obj(
          "code"   -> "NOT_FOUND",
          "reason" -> "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
        )

        DownstreamStub.onError(
          method = DownstreamStub.GET,
          uri = listDownstreamUri,
          queryParams = listDownstreamQueryParams,
          errorStatus = NOT_FOUND,
          errorBody = notFoundReturnsErrorBody.toString()
        )

        val result: WSResponse = await(listRequest.get())
        result.status          shouldBe OK
        result.json.toString() shouldBe """{"returns":[]}"""
      }

      "return a 500 error response when financial data fails validation" in new Test {
        AuthStub.authorised()

        val sampleDesReturn: DesReturnSummary = sample[DesReturnSummary]

        DownstreamStub.onSuccess(
          method = DownstreamStub.GET,
          uri = listDownstreamUri,
          queryParams = listDownstreamQueryParams,
          status = OK,
          body = Json.obj("returnList" -> JsArray(Seq(Json.toJson(sampleDesReturn))))
        )

        taxYearQueryDates.foreach(params =>
          DownstreamStub.onSuccess(
            method = DownstreamStub.GET,
            uri = getFinancialDataDownstreamUri,
            queryParams = params,
            status = OK,
            body = Json.toJson(DesFinancialDataResponse(List(sample[DesFinancialTransaction])))
          )
        )

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe INTERNAL_SERVER_ERROR
        result.body   shouldBe ""
      }

      // Exhaustive testing of business scenarios is the responsibility of validation unit testing/ UI testing
      // Here I will only be testing a single success scenario to prove that the endpoint functions
      "return a 200 success for scenario where no charges, or financial data exists" in new Test {
        AuthStub.authorised()

        val sampleDesReturn: DesReturnSummary = DesReturnSummary(
          submissionId = "someSubmissionId",
          submissionDate = LocalDate.of(currentTaxYear, 1, 10),
          completionDate = LocalDate.of(currentTaxYear, 1, 30),
          lastUpdatedDate = None,
          taxYear = s"$currentTaxYear",
          propertyAddress = AddressDetails(
            addressLine1 = "221b Baker Street",
            addressLine2 = Some("Marylebone"),
            addressLine3 = Some("London"),
            addressLine4 = None,
            postalCode = Some("NW1 6XE"),
            countryCode = "GB"
          ),
          totalCGTLiability = 0,
          charges = None
        )

        DownstreamStub.onSuccess(
          method = DownstreamStub.GET,
          uri = listDownstreamUri,
          queryParams = listDownstreamQueryParams,
          status = OK,
          body = Json.obj("returnList" -> JsArray(Seq(Json.toJson(sampleDesReturn))))
        )

        taxYearQueryDates.foreach(params =>
          DownstreamStub.onError(
            method = DownstreamStub.GET,
            uri = getFinancialDataDownstreamUri,
            queryParams = params,
            errorStatus = NOT_FOUND,
            errorBody = notFoundFinancialDataErrorBody.toString()
          )
        )

        val amendReturnsRepository: AmendReturnsRepository = app.injector.instanceOf[DefaultAmendReturnsRepository]

        val sampleSubmitReturnRequest: SubmitReturnRequest = sample[SubmitReturnRequest]

        val sampleSubmitReturnRequestWithCgtId: SubmitReturnRequest = sampleSubmitReturnRequest.copy(
          subscribedDetails = sampleSubmitReturnRequest.subscribedDetails.copy(
            cgtReference = CgtReference(cgtRef)
          )
        )

        val sampleAmendReturnData: AmendReturnData = sample[AmendReturnData]

        val sampleAmendReturnDataWithReturnId: AmendReturnData = sampleAmendReturnData.copy(
          originalReturn = sampleAmendReturnData.originalReturn.copy(
            summary = sampleAmendReturnData.originalReturn.summary.copy(
              submissionId = "someSubmissionId"
            )
          )
        )

        val request: EitherT[Future, models.Error, Unit] = amendReturnsRepository.save(
          submitReturnRequest = sampleSubmitReturnRequestWithCgtId.copy(
            amendReturnData = Some(sampleAmendReturnDataWithReturnId)
          )
        )

        val returnSummaryModel: ReturnSummary = ReturnSummary(
          submissionId = sampleDesReturn.submissionId,
          submissionDate = sampleDesReturn.submissionDate,
          completionDate = sampleDesReturn.completionDate,
          lastUpdatedDate = None,
          taxYear = sampleDesReturn.taxYear,
          mainReturnChargeAmount = AmountInPence(0),
          mainReturnChargeReference = None,
          propertyAddress = UkAddress(
            sampleDesReturn.propertyAddress.addressLine1,
            sampleDesReturn.propertyAddress.addressLine2,
            sampleDesReturn.propertyAddress.addressLine3,
            sampleDesReturn.propertyAddress.addressLine4,
            Postcode(sampleDesReturn.propertyAddress.postalCode.get)
          ),
          charges = List.empty,
          isRecentlyAmended = true,
          expired = false
        )

        val resultJson: JsValue = Json.toJson(ListReturnsResponse(List(returnSummaryModel)))

        await(request.value).foreach { _ =>
          val result: WSResponse = await(listRequest.get())
          result.status shouldBe OK
          result.json   shouldBe resultJson
        }
      }
    }
  }

}
