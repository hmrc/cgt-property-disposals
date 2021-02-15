/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.controllers.returns

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ListReturnsResponse
import uk.gov.hmrc.cgtpropertydisposals.service.returns.ReturnsService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class GetReturnsController @Inject() (
  authenticate: AuthenticateActions,
  returnsService: ReturnsService,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def listReturns(cgtReference: String, fromDate: String, toDate: String): Action[AnyContent] =
    authenticate.async { implicit request =>
      withFromAndToDate(fromDate, toDate) { case (from, to) =>
        returnsService
          .listReturns(CgtReference(cgtReference), from, to)
          .fold(
            { e =>
              logger.warn(s"Could not get returns for cgt reference $cgtReference between $fromDate and $toDate", e)
              InternalServerError
            },
            returns => Ok(Json.toJson(ListReturnsResponse(returns)))
          )
      }
    }

  def displayReturn(cgtReference: String, submissionId: String): Action[AnyContent] =
    authenticate.async { implicit request =>
      returnsService
        .displayReturn(CgtReference(cgtReference), submissionId)
        .fold(
          { e =>
            logger.warn(s"Could not get return for cgt reference $cgtReference and submission id $submissionId", e)
            InternalServerError
          },
          completeReturn => Ok(Json.toJson(completeReturn))
        )
    }

  private def withFromAndToDate(fromDate: String, toDate: String)(
    f: (LocalDate, LocalDate) => Future[Result]
  ): Future[Result] = {
    def parseDate(string: String): Option[LocalDate] =
      Try(LocalDate.parse(string, DateTimeFormatter.ISO_DATE)).toOption

    parseDate(fromDate) -> parseDate(toDate) match {
      case (None, None) =>
        logger.warn(s"Could not parse fromDate ('$fromDate') or toDate ('$toDate') ")
        Future.successful(BadRequest)

      case (None, Some(_)) =>
        logger.warn(s"Could not parse fromDate ('$fromDate')")
        Future.successful(BadRequest)

      case (Some(_), None) =>
        logger.warn(s"Could not parse toDate ('$toDate')")
        Future.successful(BadRequest)

      case (Some(from), Some(to)) =>
        f(from, to)
    }
  }
}
