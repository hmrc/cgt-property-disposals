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

package uk.gov.hmrc.cgtpropertydisposals.controllers.returns

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.controllers.returns.TaxYearController.TaxYearResponse
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.TaxYearGen.taxYearGen
import uk.gov.hmrc.cgtpropertydisposals.service.returns.TaxYearService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class TaxYearControllerSpec extends ControllerSpec {

  private val mockTaxYearService = mock[TaxYearService]

  private def mockGetTaxYear(date: LocalDate)(response: Option[TaxYear]) =
    when(
      mockTaxYearService
        .getTaxYear(date)
    ).thenReturn(response)

  val controller = new TaxYearController(
    Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    mockTaxYearService,
    stubControllerComponents()
  )

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    HeaderCarrier(),
    FakeRequest()
  )

  "TaxYearController" when {
    "handling requests to get tax years" must {
      def performAction(date: String): Future[Result] =
        controller.taxYear(date)(request)

      "return a bad request" when {
        "the date cannot be parsed" in {
          status(performAction("abc")) shouldBe BAD_REQUEST
        }
      }

      "return an ok response" when {
        val (date, dateString) = LocalDate.of(2020, 1, 2) -> "2020-01-02"

        "a tax year is found" in {
          val taxYear = sample[TaxYear]
          mockGetTaxYear(date)(Some(taxYear))

          val result = performAction(dateString)
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(TaxYearResponse(Some(taxYear)))
        }

        "a tax year is not found" in {
          mockGetTaxYear(date)(None)

          val result = performAction(dateString)
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(TaxYearResponse(None))
        }
      }
    }
  }
}
