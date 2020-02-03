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

package uk.gov.hmrc.cgtpropertydisposals.repositories.returns

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cgtpropertydisposals.config.AppConfig
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.CacheRepository
import uk.gov.hmrc.mongo.ReactiveRepository

@ImplementedBy(classOf[DefaultDraftReturnsRepository])
trait DraftReturnsRepository {
  def save(draftReturn: DraftReturn): EitherT[Future, Error, Unit]
}

@Singleton
class DefaultDraftReturnsRepository @Inject() (component: ReactiveMongoComponent, appConfig: AppConfig)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[DraftReturn, BSONObjectID](
      collectionName = "draft-returns",
      mongo          = component.mongoConnector.db,
      domainFormat   = DraftReturn.format
    )
    with DraftReturnsRepository
    with CacheRepository[DraftReturn] {

  val cacheTtl: Int     = appConfig.mongoSessionExpireAfterSeconds
  val indexName: String = "draft-return-cache-ttl"
  val objName: String   = "return"

  override def save(draftReturn: DraftReturn): EitherT[Future, Error, Unit] =
    EitherT(
      set(draftReturn.id.toString, Json.toJson(draftReturn))
    )
}
