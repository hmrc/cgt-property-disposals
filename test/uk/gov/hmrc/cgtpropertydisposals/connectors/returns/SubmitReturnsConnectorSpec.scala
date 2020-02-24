package uk.gov.hmrc.cgtpropertydisposals.connectors.returns

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers.{await, _}
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.NumberOfProperties.One
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class SubmitReturnsConnectorSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      returns {
         |        protocol = http
         |        host     = localhost
         |        port     = 7022
         |    }
         |  }
         |}
         |
         |des {
         |  bearer-token = $desBearerToken
         |  environment  = $desEnvironment
         |}
         |""".stripMargin
    )
  )

  val submitReturnRequest: SubmitReturnRequest = {
    sample[SubmitReturnRequest].copy(completeReturn = sample[CompleteReturn]
      .copy(triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(numberOfProperties = One))
    )
  }

  val connector = new SubmitReturnsConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  "SubmitReturnsConnectorImpl" when {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Map("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    def expectedSubmitReturnUrl(cgtReference: String) =
      s"""http://localhost:7022/cgt-reference/$cgtReference/return"""

    "handling request to submit return" must {

      "do a post http call and get the result" in {
        List(
          HttpResponse(200),
          HttpResponse(400),
          HttpResponse(401),
          HttpResponse(403),
          HttpResponse(500),
          HttpResponse(502),
          HttpResponse(503)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPost(
              expectedSubmitReturnUrl(submitReturnRequest.subscribedDetails.cgtReference.value),
              expectedHeaders,
              *
            )(
              Some(httpResponse)
            )

            await(connector.submit(submitReturnRequest).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error" when {

        "the call fails" in {
          mockPost(
            expectedSubmitReturnUrl(submitReturnRequest.subscribedDetails.cgtReference.value),
            expectedHeaders,
            *
          )(None)

          await(connector.submit(submitReturnRequest).value).isLeft shouldBe true
        }
      }

    }
  }
}
