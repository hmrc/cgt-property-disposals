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

package uk.gov.hmrc.cgtpropertydisposals.http

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.HttpVerbs.GET
import play.api.libs.ws
import play.api.libs.ws.{BodyWritable, WSClient}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

@ImplementedBy(classOf[PlayHttpClientImpl])
trait PlayHttpClient {

  def get(url: String, headers: Seq[(String, String)], timeout: Duration): Future[ws.WSResponse]
  def post[A](url: String, headers: Seq[(String, String)], body: A)(implicit
    bodyWritable: BodyWritable[A]
  ): Future[ws.WSResponse]

}

@Singleton
class PlayHttpClientImpl @Inject() (wsClient: WSClient) extends PlayHttpClient {
  override def get(url: String, headers: Seq[(String, String)], timeout: Duration): Future[ws.WSResponse] =
    wsClient
      .url(url)
      .withMethod(GET)
      .withHttpHeaders(headers: _*)
      .withRequestTimeout(timeout)
      .stream()

  override def post[A](url: String, headers: Seq[(String, String)], body: A)(implicit
    bodyWritable: BodyWritable[A]
  ): Future[ws.WSResponse] =
    wsClient
      .url(url)
      .withHttpHeaders(headers: _*)
      .post(body)
}
