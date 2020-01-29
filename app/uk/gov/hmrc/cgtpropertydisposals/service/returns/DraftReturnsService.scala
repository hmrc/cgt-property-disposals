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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DraftReturnsRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DraftReturnsServiceImpl])
trait DraftReturnsService {

  def saveDraftReturn(draftReturn: DraftReturn)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, Error, Unit]

}

@Singleton
class DraftReturnsServiceImpl @Inject() (
  draftReturnRepository: DraftReturnsRepository,
  metrics: Metrics
) extends DraftReturnsService
    with Logging {

  override def saveDraftReturn(draftReturn: DraftReturn)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, Error, Unit] = {
    draftReturnRepository
      .save(draftReturn)
      .leftMap(error => Error(s"Could not store draft return: $error"))
  }

}
