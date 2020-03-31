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

package uk.gov.hmrc.cgtpropertydisposals.controllers.testonly

import java.util.Base64

import cats.instances.future._
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.dms.B64Html
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.service.DmsSubmissionService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext

class DmsController @Inject() (
  authenticate: AuthenticateActions,
  dmsSubmissionService: DmsSubmissionService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def testSubmitToDms(): Action[AnyContent] = authenticate.async { implicit request =>
    val html =
      """
        |<!doctype html>
        |<html lang="en">
        |<head>
        |  <meta charset="utf-8">
        |  <title>CGT DMS TEST SUBMISSION</title>
        |  <meta name="description" content="The HTML5 Herald">
        |  <meta name="author" content="SitePoint">
        |</head>
        |<body>
        |<h1> This is a test CGT file </h1>
        |</body>
        |</html>
        |""".stripMargin

    dmsSubmissionService
      .testSubmitToDms(
        B64Html(Base64.getEncoder().encodeToString(html.getBytes())),
        cgtReference = CgtReference("XXCGTP123456789"),
        formBundleId = "012345678901"
      )
      .fold(
        error => {
          logger.error(s"Dms submission failed with $error")
          BadRequest
        },
        envelopeId => {
          logger.info(s"DMS submission succeeded ${envelopeId.value}")
          Ok
        }
      )
  }
}
