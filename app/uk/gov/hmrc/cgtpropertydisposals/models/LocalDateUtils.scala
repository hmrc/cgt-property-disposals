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

package uk.gov.hmrc.cgtpropertydisposals.models

import java.time.LocalDate

import cats.Order

object LocalDateUtils {
  val monthNames: Map[Int, String] = Map(
    1  -> "January",
    2  -> "February",
    3  -> "March",
    4  -> "April",
    5  -> "May",
    6  -> "June",
    7  -> "July",
    8  -> "August",
    9  -> "September",
    10 -> "October",
    11 -> "November",
    12 -> "December"
  )

  val monthNamesInShort: Map[Int, String] = Map(
    1  -> "Jan",
    2  -> "Feb",
    3  -> "Mar",
    4  -> "Apr",
    5  -> "May",
    6  -> "Jun",
    7  -> "Jul",
    8  -> "Aug",
    9  -> "Sept",
    10 -> "Oct",
    11 -> "Nov",
    12 -> "Dec"
  )

  def govDisplayFormat(date: LocalDate): String =
    s"""${date.getDayOfMonth()} ${monthNames(date.getMonthValue())} ${date.getYear()}"""

  def govShortDisplayFormat(date: LocalDate): String =
    s"""${date.getDayOfMonth()} ${monthNamesInShort(date.getMonthValue())} ${date.getYear()}"""

  implicit val order: Order[LocalDate] = Order.from(_ compareTo _)

}
