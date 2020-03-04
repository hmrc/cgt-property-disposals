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

package uk.gov.hmrc.cgtpropertydisposals.controllers.upscan

import cats.instances.future._
import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBackEvent._
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{FileDescriptorId, UpscanCallBackEvent, UpscanFileDescriptor, UpscanSnapshot}
import uk.gov.hmrc.cgtpropertydisposals.service.UpscanService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class UpscanController @Inject() (
  authenticate: AuthenticateActions,
  upscanService: UpscanService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def getUpscanSnapshot(cgtReference: CgtReference): Action[AnyContent] =
    authenticate.async {
      upscanService
        .getUpscanSnapshot(cgtReference)
        .fold(
          e => {
            logger.warn(s"failed to get upscan snapshot $e")
            InternalServerError
          },
          snapshot => Ok(Json.toJson[UpscanSnapshot](snapshot))
        )
    }

  def getUpscanFileDescriptor(fileDescriptorId: FileDescriptorId): Action[AnyContent] =
    authenticate.async {
      upscanService
        .getUpscanFileDescriptor(fileDescriptorId)
        .fold(
          e => {
            logger.warn(s"failed to get upscan file descriptor $e")
            InternalServerError
          }, {
            case Some(fd) => Ok(Json.toJson[UpscanFileDescriptor](fd))
            case None => {
              logger.info(s"could not find upscan file descriptor with upscan reference: ${fileDescriptorId.value}")
              BadRequest
            }
          }
        )
    }

  def updateUpscanFileDescriptorStatus(): Action[JsValue] =
    authenticate(parse.json).async { implicit request =>
      request.body
        .asOpt[UpscanFileDescriptor] match {
        case Some(fd) =>
          upscanService
            .updateUpscanFileDescriptorStatus(fd)
            .fold(
              e => {
                logger.warn(s"failed to update upscan file descriptor details: $e")
                InternalServerError
              },
              _ => Ok
            )
        case None => {
          logger.warn(s"failed to parse upscan file descriptor JSON payload")
          Future.successful(BadRequest)
        }
      }
    }

  def saveUpscanFileDescriptor(): Action[JsValue] = authenticate(parse.json).async { implicit request =>
    request.body
      .asOpt[UpscanFileDescriptor] match {
      case Some(fd) =>
        upscanService
          .storeFileDescriptorData(fd)
          .fold(
            e => {
              logger.warn(s"failed to save upscan file descriptor details: $e")
              InternalServerError
            },
            _ => Ok
          )
      case None => {
        logger.warn(s"failed to parse upscan file descriptor JSON payload")
        Future.successful(BadRequest)
      }
    }
  }

  def callback(cgtReference: CgtReference): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      request.body
        .validate[UpscanCallBackEvent]
        .fold(
          error => Future.successful(BadRequest(s"failed to parse upscan call back result response: $error")),
          upscanResult =>
            upscanService.saveCallBackData(toUpscanCallBack(cgtReference, upscanResult)).value.map {
              case Left(error) =>
                logger.warn(s"failed to save upscan call back result response $error")
                InternalServerError
              case Right(_) => NoContent
            }
        )
    }

}
