/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.ws.{WSRequest, WSResponse}
import stubs.{AuthStub, DownstreamStub}
import support.IntegrationBaseSpec
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{desFinancialTransactionGen, desReturnSummaryGen, sample}
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesFinancialTransaction
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.DesReturnSummary

class GetReturnsControllerISpec extends IntegrationBaseSpec {

  trait Test {
    val cgtRef: String = "dummyCgtRef"
    val fromDate: String = "2024-01-01"
    val toDate: String = "2024-02-01"

    val listRouteUri: String = s"/returns/$cgtRef/$fromDate/$toDate"
    val listDownstreamUri: String = s"/capital-gains-tax/returns/$cgtRef"
    val listDownstreamQueryParams: Map[String, String] = Map("fromDate" -> fromDate, "toDate" -> toDate)

    val getFinancialDataDownstreamUri: String = s"/enterprise/financial-data/ZCGT/$cgtRef/CGT"

    def getTaxYearQueryDates(startYear: Int): Map[String, String] = Map(
      "dateFrom" -> s"$startYear-04-06",
      "dateTo" -> s"${startYear + 1}-04-05"
    )

    val taxYearDates2020: Map[String, String] = getTaxYearQueryDates(2020)
    val taxYearDates2021: Map[String, String] = getTaxYearQueryDates(2021)
    val taxYearDates2022: Map[String, String] = getTaxYearQueryDates(2022)
    val taxYearDates2023: Map[String, String] = getTaxYearQueryDates(2023)
    val taxYearDates2024: Map[String, String] = getTaxYearQueryDates(2024)

    val taxYearQueryDates: Seq[Map[String, String]] = Seq(
      taxYearDates2020, taxYearDates2021, taxYearDates2022, taxYearDates2023, taxYearDates2024
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
        result.body shouldBe "Forbidden"
      }
    }

    "there are errors in the request" must {
      "[fromDate improperly formatted] return 400 error response with no body" in new Test {
        AuthStub.authorised()
        override val fromDate: String = "wrongFormat"

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe BAD_REQUEST
        result.body shouldBe ""
      }

      "[toDate improperly formatted] return 400 error response with no body" in new Test {
        AuthStub.authorised()
        override val toDate: String = "wrongFormat"

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe BAD_REQUEST
        result.body shouldBe ""
      }

      "[fromDate & toDate improperly formatted] return 400 error response with no body" in new Test {
        AuthStub.authorised()
        override val fromDate: String = "wrongFormat"
        override val toDate: String = "wrongFormat"

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe BAD_REQUEST
        result.body shouldBe ""
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
        result.body shouldBe ""
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
        result.body shouldBe ""
      }

      "return a 500 error response when returns exist but financial data does not" in new Test {
        AuthStub.authorised()

        val notFoundDataErrorBody: JsObject = Json.obj(
          "code" -> "NOT_FOUND",
          "reason" -> "The remote endpoint has indicated that no data can be found."
        )

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
            errorBody = notFoundDataErrorBody.toString()
          )
        )

        val result: WSResponse = await(listRequest.get())
        result.status shouldBe INTERNAL_SERVER_ERROR
        result.body shouldBe ""
      }

      "return the expected result when a CGT reference is in use but no returns are found" in new Test {
        AuthStub.authorised()

        val notFoundReturnsErrorBody: JsObject = Json.obj(
          "code" -> "NOT_FOUND",
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
        result.status shouldBe OK
        result.json.toString() shouldBe """{"returns":[]}"""
      }
    }
  }

  "/returns/:cgtReference/:submissionId" when {

  }

}
