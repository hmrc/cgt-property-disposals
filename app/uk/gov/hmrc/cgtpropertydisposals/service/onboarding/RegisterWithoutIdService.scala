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

package uk.gov.hmrc.cgtpropertydisposals.service.onboarding

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.OK
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.RegisterWithoutIdConnector
import uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding._
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.ids.SapNumber
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, UUIDGenerator}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.RegisterWithoutIdServiceImpl.RegisterWithoutIdResponse
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegisterWithoutIdServiceImpl])
trait RegisterWithoutIdService {

  def registerWithoutId(registrationDetails: RegistrationDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SapNumber]

}

@Singleton
class RegisterWithoutIdServiceImpl @Inject() (
  connector: RegisterWithoutIdConnector,
  uuidGenerator: UUIDGenerator,
  auditService: AuditService,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends RegisterWithoutIdService
    with Logging {

  def registerWithoutId(registrationDetails: RegistrationDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SapNumber] = {
    val referenceId = uuidGenerator.nextId()
    val timer       = metrics.registerWithoutIdTimer.time()

    connector.registerWithoutId(registrationDetails, referenceId).subflatMap { response =>
      timer.close()
      auditService.sendRegistrationResponse(
        response.status,
        response.body,
        routes.SubscriptionController.registerWithoutId().url
      )

      if (response.status === OK) {
        response
          .parseJSON[RegisterWithoutIdResponse]()
          .bimap(
            { e =>
              metrics.registerWithIdErrorCounter.inc()
              Error(e)
            }, { response =>
              logger.info(
                s"For acknowledgement reference id $referenceId, register with id was successful with sap number ${response.sapNumber}"
              )
              SapNumber(response.sapNumber)
            }
          )
      } else {
        metrics.registerWithIdErrorCounter.inc()
        Left(Error(s"call to register with id with reference id $referenceId came back with status ${response.status}"))
      }
    }
  }

}

object RegisterWithoutIdServiceImpl {

  final case class RegisterWithoutIdResponse(sapNumber: String)

  implicit val responseReads: Reads[RegisterWithoutIdResponse] = Json.reads[RegisterWithoutIdResponse]

}
