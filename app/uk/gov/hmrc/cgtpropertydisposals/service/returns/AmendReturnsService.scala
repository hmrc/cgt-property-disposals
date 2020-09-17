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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{AmendReturnType, CreateReturnType, ReturnType}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.AmendReturnsRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultAmendReturnsService])
trait AmendReturnsService {
  def getAmendedReturn(cgtReference: CgtReference): EitherT[Future, Error, List[SubmitReturnRequest]]

  def saveAmendedReturn(
    submitReturnRequest: SubmitReturnRequest
  ): EitherT[Future, Error, Unit]
}

@Singleton
class DefaultAmendReturnsService @Inject() (
  amendReturnsRepository: AmendReturnsRepository
) extends AmendReturnsService
    with Logging {

  def getAmendedReturn(cgtReference: CgtReference): EitherT[Future, Error, List[SubmitReturnRequest]] =
    amendReturnsRepository.fetch(cgtReference)

  override def saveAmendedReturn(
    submitReturnRequest: SubmitReturnRequest
  ): EitherT[Future, Error, Unit] =
    ReturnType(submitReturnRequest) match {
      case CreateReturnType(_)   => EitherT[Future, Error, Unit](Future.successful(Right(())))
      case AmendReturnType(_, _) =>
        amendReturnsRepository.save(submitReturnRequest, submitReturnRequest.subscribedDetails.cgtReference)
    }

}
