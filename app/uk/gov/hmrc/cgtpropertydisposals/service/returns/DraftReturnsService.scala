/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.UUID
import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DraftReturnsRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultDraftReturnsService])
trait DraftReturnsService {

  def getDraftReturn(cgtReference: CgtReference): EitherT[Future, Error, Seq[DraftReturn]]

  def saveDraftReturn(draftReturn: DraftReturn, cgtReference: CgtReference): EitherT[Future, Error, Unit]

  def deleteDraftReturns(draftReturnIds: List[UUID]): EitherT[Future, Error, Unit]

  def deleteDraftReturn(cgtReference: CgtReference): EitherT[Future, Error, Unit]
}

@Singleton
class DefaultDraftReturnsService @Inject() (
  draftReturnRepository: DraftReturnsRepository
) extends DraftReturnsService
    with Logging {

  def getDraftReturn(cgtReference: CgtReference): EitherT[Future, Error, Seq[DraftReturn]] =
    draftReturnRepository.fetch(cgtReference)

  override def saveDraftReturn(draftReturn: DraftReturn, cgtReference: CgtReference): EitherT[Future, Error, Unit] =
    draftReturnRepository.save(draftReturn, cgtReference)

  override def deleteDraftReturns(draftReturnIds: List[UUID]): EitherT[Future, Error, Unit] =
    draftReturnRepository.deleteAll(draftReturnIds)

  override def deleteDraftReturn(cgtReference: CgtReference): EitherT[Future, Error, Unit] =
    draftReturnRepository.delete(cgtReference)
}
