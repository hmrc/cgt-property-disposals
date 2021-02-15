/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class EnrolmentStoreProxyConnectorImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val config = Configuration(
    ConfigFactory.parseString(
      """
        |microservice {
        |  services {
        |    enrolment-store-proxy {
        |      protocol = http
        |      host     = host
        |      port     = 123
        |    }
        |  }
        |}
        |""".stripMargin
    )
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val connector = new EnrolmentStoreProxyConnectorImpl(mockHttp, new ServicesConfig(config))

  "EnrolmentStoreProxyConnectorImpl" when {

    "handling requests to get all principal enrolments for a cgt reference" must {

      val emptyJsonBody = "{}"

      def expectedUrl(cgtReference: CgtReference) =
        s"http://host:123/enrolment-store-proxy/enrolment-store/enrolments/HMRC-CGT-PD~CGTPDRef~${cgtReference.value}/users"

      val expectedQueryParameters = List("type" -> "principal")

      "do a GET http call and get the result" in {
        val cgtReference = sample[CgtReference]

        List(
          HttpResponse(200, emptyJsonBody),
          HttpResponse(400, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(403, emptyJsonBody),
          HttpResponse(500, emptyJsonBody),
          HttpResponse(502, emptyJsonBody),
          HttpResponse(503, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockGetWithQueryWithHeaders(
              expectedUrl(cgtReference),
              expectedQueryParameters,
              Seq.empty
            )(
              Some(httpResponse)
            )

            await(connector.getPrincipalEnrolments(cgtReference).value) shouldBe Right(httpResponse)
          }
        }
      }
    }

  }

}
