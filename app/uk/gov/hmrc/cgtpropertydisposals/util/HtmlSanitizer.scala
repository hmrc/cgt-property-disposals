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

package uk.gov.hmrc.cgtpropertydisposals.util

import org.owasp.html.HtmlPolicyBuilder

import scala.util.Try

object HtmlSanitizer {

  private val allowedElements = List(
    "a",
    "label",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "p",
    "i",
    "b",
    "u",
    "strong",
    "em",
    "small",
    "big",
    "pre",
    "code",
    "cite",
    "samp",
    "sub",
    "sup",
    "strike",
    "center",
    "blockquote",
    "hr",
    "br",
    "col",
    "font",
    "span",
    "div",
    "ul",
    "ol",
    "li",
    "dd",
    "dt",
    "dl",
    "tbody",
    "thead",
    "tfoot",
    "table",
    "td",
    "th",
    "tr",
    "colgroup",
    "fieldset",
    "legend",
    "html"
  )

  private val blockedElements = List(
    "img",
    "link",
    "meta",
    "script",
    "a",
    "header",
    "service-info",
    "footer-wrapper",
    "global-cookie-message",
    "global-app-error",
    "print-hidden",
    "report-error"
  )

  private val policyFactory = new HtmlPolicyBuilder()
    .disallowElements(blockedElements: _*)
    .allowElements(allowedElements: _*)
    .toFactory

  def sanitize(html: String): Option[String] =
    Try(
      policyFactory.sanitize(html)
    ).toOption

}
