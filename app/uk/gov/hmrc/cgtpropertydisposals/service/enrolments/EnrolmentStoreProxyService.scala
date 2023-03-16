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

package uk.gov.hmrc.cgtpropertydisposals.service.enrolments

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments.EnrolmentStoreProxyConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EnrolmentStoreProxyServiceImpl])
trait EnrolmentStoreProxyService {

  def cgtEnrolmentExists(cgtReference: CgtReference)(implicit hc: HeaderCarrier): EitherT[Future, Error, Boolean]

}

@Singleton
class EnrolmentStoreProxyServiceImpl @Inject() (
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector
)(implicit ec: ExecutionContext)
    extends EnrolmentStoreProxyService
    with Logging {

  def cgtEnrolmentExists(cgtReference: CgtReference)(implicit hc: HeaderCarrier): EitherT[Future, Error, Boolean] =
    enrolmentStoreProxyConnector.getPrincipalEnrolments(cgtReference).subflatMap { httpResponse =>
      if (httpResponse.status === OK) Right(true)
      else if (httpResponse.status === NO_CONTENT) Right(false)
      else Left(Error(s"Call to check for principal enrolments came back with status ${httpResponse.status}"))
    }

}
