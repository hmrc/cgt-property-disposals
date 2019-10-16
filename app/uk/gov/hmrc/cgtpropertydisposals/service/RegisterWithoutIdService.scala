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

package uk.gov.hmrc.cgtpropertydisposals.service

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cgtpropertydisposals.connectors.RegisterWithoutIdConnector
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, RegistrationDetails, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.service.RegisterWithoutIdServiceImpl.RegisterWithoutIdResponse
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegisterWithoutIdServiceImpl])
trait RegisterWithoutIdService {

  def registerWithoutId(registrationDetails: RegistrationDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SapNumber]

}

@Singleton
class RegisterWithoutIdServiceImpl @Inject()(connector: RegisterWithoutIdConnector)(implicit ec: ExecutionContext)
    extends RegisterWithoutIdService {

  def registerWithoutId(registrationDetails: RegistrationDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SapNumber] =
    connector.registerWithoutId(registrationDetails).subflatMap { response =>
      if (response.status === 200) {
        response
          .parseJSON[RegisterWithoutIdResponse]()
          .bimap(
            Error(_),
            response => SapNumber(response.sapNumber)
          )
      } else {
        Left(Error(s"call to register with id came back with status ${response.status}"))
      }
    }

}

object RegisterWithoutIdServiceImpl {

  final case class RegisterWithoutIdResponse(sapNumber: String)

  implicit val responseReads: Reads[RegisterWithoutIdResponse] = Json.reads[RegisterWithoutIdResponse]

}
