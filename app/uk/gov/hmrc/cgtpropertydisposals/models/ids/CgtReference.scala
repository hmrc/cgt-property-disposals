/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.ids

import play.api.libs.json.{Json, OFormat}
import play.api.mvc.PathBindable
import cats.syntax.either._

final case class CgtReference(value: String) extends AnyVal

object CgtReference {

  implicit val binder: PathBindable[CgtReference] =
    new PathBindable[CgtReference] {
      val stringBinder: PathBindable[String] = implicitly[PathBindable[String]]

      override def bind(key: String, value: String): Either[String, CgtReference] =
        stringBinder.bind(key, value).map(CgtReference.apply)

      override def unbind(key: String, value: CgtReference): String =
        stringBinder.unbind(key, value.value)
    }

  implicit val format: OFormat[CgtReference] = Json.format[CgtReference]
}