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

package uk.gov.hmrc.cgtpropertydisposals.models.des

import cats.Eq
import cats.instances.string._
import cats.syntax.eq._
import play.api.libs.json.{Json, Reads}

sealed trait DesErrorResponse extends Product with Serializable

object DesErrorResponse {

  final case class SingleDesErrorResponse(code: String, reason: String) extends DesErrorResponse

  final case class MultipleDesErrorsResponse(failures: List[SingleDesErrorResponse]) extends DesErrorResponse

  implicit val singleDesErrorResponseEq: Eq[SingleDesErrorResponse] = Eq.fromUniversalEquals

  implicit class DesErrorResponseOps(private val e: DesErrorResponse) extends AnyVal {

    def fold[A](ifSingle: SingleDesErrorResponse => A, ifMultiple: MultipleDesErrorsResponse => A): A =
      e match {
        case s: SingleDesErrorResponse    => ifSingle(s)
        case m: MultipleDesErrorsResponse => ifMultiple(m)
      }

    def hasError(error: SingleDesErrorResponse): Boolean =
      fold(
        _ === error,
        _.failures.contains(error)
      )

    def hasCode(code: String): Boolean =
      fold(
        _.code === code,
        _.failures.exists(_.code === code)
      )

  }

  implicit val singleErrorResponseReads: Reads[SingleDesErrorResponse]      = Json.reads
  implicit val multipleErrorResponseReads: Reads[MultipleDesErrorsResponse] = Json.reads
  implicit val errorResponseReads: Reads[DesErrorResponse]                  =
    Reads(j => singleErrorResponseReads.reads(j).orElse(multipleErrorResponseReads.reads(j)))
}
