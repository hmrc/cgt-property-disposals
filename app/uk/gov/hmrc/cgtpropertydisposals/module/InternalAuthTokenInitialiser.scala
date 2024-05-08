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

package uk.gov.hmrc.cgtpropertydisposals.module

import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

abstract class InternalAuthTokenInitialiser

@Singleton
class NoOpInternalAuthTokenInitialiser @Inject() extends InternalAuthTokenInitialiser

@Singleton
class InternalAuthTokenInitialiserImpl @Inject() (
  configuration: Configuration,
  httpClient: HttpClientV2
)(implicit ec: ExecutionContext)
    extends InternalAuthTokenInitialiser
    with Logging {

  private val internalAuthHost =
    configuration.get[String]("microservice.services.internal-auth.host")

  private val internalAuthPort =
    configuration.get[String]("microservice.services.internal-auth.port")

  private val authToken =
    configuration.get[String]("internal-auth.token")

  private val appName =
    configuration.get[String]("appName")

  private def createClientAuthTokens() = {
    def createClientAuthToken(token: JsObject) = {
      logger.info("Creating token " + token.toString())
      httpClient
        .post(url"http://$internalAuthHost:$internalAuthPort/test-only/token")(HeaderCarrier())
        .withBody(token)
        .execute
        .flatMap { response =>
          if (response.status == 201) {
            logger.info("Auth token initialised")
            Future.unit
          } else {
            logger.error("Unable to initialise internal-auth token " + response.body)
            Future.failed(new RuntimeException("Unable to initialise internal-auth token"))
          }
        }
    }

    def createClientAuthGrant(grant: JsValue) = {
      logger.info("Creating grant " + grant.toString())
      httpClient
        .put(url"http://$internalAuthHost:$internalAuthPort/test-only/grants")(HeaderCarrier())
        .withBody(grant)
        .execute
        .flatMap { response =>
          if (response.status == 204) {
            logger.info("Auth grant initialised")
            Future.unit
          } else {
            logger.error("Unable to initialise internal-auth grant " + response.body)
            Future.failed(new RuntimeException("Unable to initialise internal-auth grant"))
          }
        }
    }

    logger.info("Initialising internal auth grant and token")

    val permission = Json.obj(
      "resourceType"     -> "dms-submission",
      "resourceLocation" -> "submit",
      "actions"          -> Seq("WRITE")
    )

    for {
//      _ <- createClientAuthGrant(
//             Json.arr(
//               Json.obj(
//                 "grantees"    -> Seq(
//                   Json.obj(
//                     "granteeType" -> "service",
//                     "identifier"  -> appName
//                   )
//                 ),
//                 "permissions" -> Seq(
//                   permission
//                 )
//               )
//             )
//           )
      _ <- createClientAuthToken(
             Json.obj(
               "token"       -> authToken,
               "principal"   -> appName,
               "permissions" -> Seq(
                 permission
               )
             )
           )
    } yield Future.unit
  }

  createClientAuthTokens()
}
