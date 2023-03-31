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

package uk.gov.hmrc.cgtpropertydisposals.controllers.returns

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.B64Html
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeReferenceId._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{RepresenteeDetails, SubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.service.dms.{DmsSubmissionRequest, DmsSubmissionService}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{DraftReturnsService, ReturnsService}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.cgtpropertydisposals.util.{HtmlSanitizer, Logging}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitReturnsController @Inject() (
  authenticate: AuthenticateActions,
  draftReturnsService: DraftReturnsService,
  returnsService: ReturnsService,
  dmsSubmissionService: DmsSubmissionService,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def submitReturn: Action[JsValue] =
    authenticate(parse.json).async { implicit request =>
      withJsonBody[SubmitReturnRequest] { returnRequest =>
        val result =
          for {
            sanitisedHtml      <- EitherT.fromEither[Future](sanitiseHtml(returnRequest.checkYourAnswerPageHtml))
            representeeDetails <- extractRepresenteeAnswersWithValidId(returnRequest)
            submissionResult   <- returnsService.submitReturn(returnRequest, representeeDetails)
            _                  <- dmsSubmissionService.enqueue(
                                    DmsSubmissionRequest(
                                      sanitisedHtml,
                                      submissionResult.formBundleId,
                                      returnRequest.subscribedDetails.cgtReference,
                                      returnRequest.completeReturn
                                    )
                                  )
            _                   = logger.info(
                                    s"Enqueued documents to be submitted with details 'formBundleId' :CGTSUBMITDOC" +
                                      s"'cgtRef' : ${returnRequest.subscribedDetails.cgtReference}]"
                                  )
            _                  <- draftReturnsService.deleteDraftReturns(List(returnRequest.id))
          } yield submissionResult

        result.fold(
          e =>
            e.value match {
              case Left(value) =>
                value match {
                  case "Amend deadline has passed" =>
                    logger.warn(value)
                    BadRequest(value)
                  case _                           =>
                    logger.warn("Could not submit return", e)
                    InternalServerError
                }
              case Right(_)    =>
                logger.warn("Could not submit return", e)
                InternalServerError
            },
          s => Ok(Json.toJson(s))
        )
      }
    }

  private def sanitiseHtml(html: B64Html): Either[Error, B64Html] = {
    val decoded   = new String(Base64.getDecoder.decode(html.value))
    val sanitised = HtmlSanitizer.sanitize(decoded)
    val result    = sanitised.map(s => B64Html(new String(Base64.getEncoder.encode(s.getBytes()))))
    Either.fromOption(result, Error("Could not sanitise html"))
  }

  private def extractRepresenteeAnswersWithValidId(
    submitReturnRequest: SubmitReturnRequest
  ): EitherT[Future, Error, Option[RepresenteeDetails]] =
    submitReturnRequest.completeReturn
      .fold(
        _.representeeAnswers,
        _.representeeAnswers,
        _.representeeAnswers,
        _.representeeAnswers,
        _.representeeAnswers
      ) match {
      case None                                => EitherT.pure(None)
      case Some(c: CompleteRepresenteeAnswers) =>
        c.id match {
          case RepresenteeSautr(sautr)         => EitherT.pure(Some(RepresenteeDetails(c, Left(sautr))))
          case RepresenteeNino(nino)           => EitherT.pure(Some(RepresenteeDetails(c, Right(Left(nino)))))
          case RepresenteeCgtReference(cgtRef) => EitherT.pure(Some(RepresenteeDetails(c, Right(Right(cgtRef)))))
          case NoReferenceId                   => EitherT.leftT(Error("No reference id provided for representee"))
        }

    }

}
