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

package uk.gov.hmrc.cgtpropertydisposals.controllers.homepage

import java.time.LocalDateTime

import akka.stream.Materializer
import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.JsValue
import play.api.mvc.Headers
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.Helpers.CONTENT_TYPE
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.des.homepage.{FinancialDataRequest, FinancialDataResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.homepage.FinancialDataService
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataControllerSpec extends ControllerSpec {

  val financialDataService = mock[FinancialDataService]
  val auditService         = mock[AuditService]

  val financialDataRequest = sample[FinancialDataRequest]

  implicit val headerCarrier = HeaderCarrier()

  def mockGetFinancialDataService(financialData: FinancialDataRequest)(response: Either[Error, FinancialDataResponse]) =
    (financialDataService
      .getFinancialData(_: FinancialDataRequest))
      .expects(financialData)
      .returning(EitherT.fromEither[Future](response))

  implicit lazy val mat: Materializer = fakeApplication.materializer

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  def fakeRequestWithJsonBody(body: JsValue) = request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new FinancialDataController(
    authenticate         = Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    financialDataService = financialDataService,
    auditService         = auditService,
    cc                   = Helpers.stubControllerComponents()
  )

  "FinancialDataController" when {

    "handling requests to get financial data" must {}

  }

}
