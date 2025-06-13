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

package uk.gov.hmrc.cgtpropertydisposals.models.http

import cats.instances.string._
import cats.syntax.eq._
import uk.gov.hmrc.http.HeaderCarrier

sealed trait AcceptLanguage

object AcceptLanguage {
  case object EN extends AcceptLanguage

  case object CY extends AcceptLanguage

  def fromHeaderCarrier(headerCarrier: HeaderCarrier): Option[AcceptLanguage] =
    headerCarrier.otherHeaders.toMap
      .map { case (k, v) => k.toLowerCase -> v.toLowerCase }
      .get("accept-language") match {
      case Some(acceptLange) =>
        if acceptLange === "cy" then Some(CY) else if acceptLange === "en" then Some(EN) else None
      case None              => None
    }
}
