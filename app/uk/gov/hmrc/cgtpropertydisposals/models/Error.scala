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

package uk.gov.hmrc.cgtpropertydisposals.models

import play.api.libs.json.*
import uk.gov.hmrc.cgtpropertydisposals.models.Error.*

final case class Error(value: Either[String, Throwable], identifiers: Map[IdKey, IdValue])

object Error {

  private type IdKey   = String
  private type IdValue = String

  def apply(message: String, identifiers: (IdKey, IdValue)*): Error = Error(Left(message), identifiers.toMap)

  def apply(error: Throwable, identifiers: (IdKey, IdValue)*): Error = Error(Right(error), identifiers.toMap)

  implicit val errorFormat: OFormat[Error] = Json.format[Error]

  implicit val throwableFormat: OFormat[Throwable] = new OFormat[Throwable] {
    override def writes(t: Throwable): JsObject = Json.obj(
      "message" -> t.getMessage,
      "type"    -> t.getClass.getName
    )

    override def reads(json: JsValue): JsResult[Throwable] = for
      msg <- (json \ "message").validate[String]
      typ <- (json \ "type").validateOpt[String]
    yield new RuntimeException(s"${typ.getOrElse("Throwable")}: $msg")
  }

  implicit val eitherFormat: Format[Either[String, Throwable]] = new Format[Either[String, Throwable]] {
    def writes(e: Either[String, Throwable]): JsValue = e match {
      case Left(str)  => Json.obj("left" -> str)
      case Right(err) => Json.obj("right" -> Json.toJson(err))
    }

    def reads(json: JsValue): JsResult[Either[String, Throwable]] =
      (json \ "left")
        .validate[String]
        .map(Left(_))
        .orElse((json \ "right").validate[Throwable].map(Right(_)))
  }

}
