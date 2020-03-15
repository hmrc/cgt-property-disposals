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

package uk.gov.hmrc.cgtpropertydisposals.models.finance

import java.util.Locale

import cats.instances.char._
import cats.syntax.either._
import cats.syntax.eq._
import play.api.data.Forms.{mapping, of}
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}

import scala.util.Try

object MoneyUtils {

  val maxAmountOfPounds: BigDecimal = BigDecimal("5e10")

  private val currencyFormatter = java.text.NumberFormat.getCurrencyInstance(Locale.UK)

  def formatAmountOfMoneyWithPoundSign(d: BigDecimal): String = currencyFormatter.format(d).stripSuffix(".00")

  def formatAmountOfMoneyWithoutPoundSign(d: BigDecimal): String =
    formatAmountOfMoneyWithPoundSign(d).replaceAllLiterally("£", "")
}
