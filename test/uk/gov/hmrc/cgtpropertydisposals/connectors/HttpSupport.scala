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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import org.mockito.stubbing.ScalaOngoingStubbing
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.http.{HttpClient, HttpResponse}

import scala.concurrent.Future

trait HttpSupport {
  this: IdiomaticMockito with Matchers ⇒

  val mockHttp: HttpClient = mock[HttpClient]

  def mockGetWithQueryWithHeaders[A](
    url: String,
    queryParams: Seq[(String, String)],
    headers: Seq[(String, String)]
  )(response: Option[A]): Any =
    mockHttp
      .GET[A](url, queryParams, headers)(*, *, *)
      .returns(
        response.fold(
          Future.failed[A](new Exception("Test exception message"))
        )(Future.successful)
      )

  def mockPost[A](url: String, headers: Seq[(String, String)], body: A)(
    result: Option[HttpResponse]
  ): ScalaOngoingStubbing[Future[HttpResponse]] =
    mockHttp
      .POST[A, HttpResponse](url, body, headers)(*, *, *, *)
      .returns(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful)
      )

  def mockPut[A](url: String, body: A)(result: Option[HttpResponse]): ScalaOngoingStubbing[Future[HttpResponse]] =
    mockHttp
      .PUT[A, HttpResponse](url, body, *)(*, *, *, *)
      .returns(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful)
      )
}
