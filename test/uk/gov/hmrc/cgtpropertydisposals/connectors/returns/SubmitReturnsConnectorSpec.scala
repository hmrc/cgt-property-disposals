package uk.gov.hmrc.cgtpropertydisposals.connectors.returns

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers.await
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{AgentReferenceNumber, CgtReference}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, SubmitReturnRequest}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class SubmitReturnsConnectorSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  val config = Configuration(
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

  val desSubmitReturnRequest = Json.parse(
    s"""
       |
       |{
       |	"ppdReturnDetails": {
       |		"returnType": {
       |			"source": "Agent",
       |			"submissionType": "New"
       |		},
       |		"returnDetails": {
       |			"customerType": "Individual",
       |			"completionDate": "1980-03-29",
       |			"isUKResident": true,
       |			"countryResidence": "United Kingdom",
       |			"numberDisposals": 3,
       |			"totalTaxableGain": 12345678912.12,
       |			"totalNetLoss": 12345678999.12,
       |			"valueAtTaxBandDetails": [{
       |					"taxRate": 18,
       |					"valueAtTaxRate": 1234567844.12
       |				},
       |				{
       |					"taxRate": 20,
       |					"valueAtTaxRate": 12345678966.12
       |				}
       |			],
       |			"totalLiability": 12345678922.12,
       |			"adjustedAmount": 12345678348.12,
       |			"totalYTDLiability": 12345674417.12,
       |			"entrepreneursRelief": 12345674514.12,
       |			"estimate": true,
       |			"repayment": true,
       |			"attachmentUpload": true,
       |			"attachmentID": "123456789",
       |			"declaration": true
       |		},
       |		"representedPersonDetails": {
       |			"capacitorPersonalRep": "Capacitor",
       |			"firstName": "John",
       |			"lastName": "Matt",
       |			"idType": "ZCGT",
       |			"idValue": "ZCGT234567",
       |			"dateOfDeath": "1989-12-29",
       |			"trustCessationDate": "1988-07-27",
       |			"trustTerminationDate": "1989-06-12",
       |			"addressDetails": {
       |				"addressLine1": "24OxfordStreet",
       |				"addressLine2": "Coundon",
       |				"addressLine3": "Coventry",
       |				"addressLine4": "Warwickshire",
       |				"countryCode": "GB",
       |				"postalCode": "TF3 4NT"
       |			},
       |			"email": "andrew.sussex@gmail.com"
       |		},
       |		"disposalDetails": [{
       |				"disposalDate": "1989-04-27",
       |				"addressDetails": {
       |					"addressLine1": "Newunion Street",
       |					"addressLine2": "Aston",
       |					"addressLine3": "Birmingham",
       |					"addressLine4": "Warwickshire",
       |					"countryCode": "AD",
       |					"postalCode": "B457TT"
       |				},
       |				"assetType": "Residential",
       |				"percentOwned": 45,
       |				"acquisitionType": "Bought",
       |				"acquiredDate": "1989-05-25",
       |				"landRegistry": true,
       |				"acquisitionPrice": 64345374514.12,
       |				"rebased": true,
       |				"rebasedAmount": 45345374514.12,
       |				"disposalType": "Auction",
       |				"disposalPrice": 72345374514.12,
       |				"improvements": true,
       |				"improvementCosts": 62345374514.12,
       |				"acquisitionFees": 52345374514.12,
       |				"disposalFees": 42345374514.12,
       |				"initialGain": 32345374514.12,
       |				"initialLoss": 23345374514.12
       |			},
       |			{
       |				"disposalDate": "1920-02-29",
       |				"addressDetails": {
       |					"addressLine1": "Newunion Street",
       |					"addressLine2": "Aston",
       |					"addressLine3": "Birmingham",
       |					"addressLine4": "Warwickshire",
       |					"countryCode": "AD",
       |					"postalCode": "B457TT"
       |				},
       |				"assetType": "Residential",
       |				"percentOwned": 57,
       |				"acquisitionType": "Bought",
       |				"acquiredDate": "1920-02-29",
       |				"landRegistry": true,
       |				"acquisitionPrice": 52745374514.12,
       |				"rebased": true,
       |				"rebasedAmount": 54345374514.12,
       |				"disposalType": "Sold",
       |				"disposalPrice": 52345374514.12,
       |				"improvements": true,
       |				"improvementCosts": 33345374514.12,
       |				"acquisitionFees": 75345374514.12,
       |				"disposalFees": 59345374514.12,
       |				"initialGain": 52745374514.12,
       |				"initialLoss": 52445374514.12
       |			}
       |		],
       |		"lossSummaryDetails": {
       |			"inYearLoss": true,
       |			"inYearLossUsed": 0.02,
       |			"preYearLoss": true,
       |			"preYearLossUsed": 0.02
       |		},
       |		"incomeAllowanceDetails": {
       |			"estimatedIncome": 0.02,
       |			"personalAllowance": 0.02,
       |			"annualExemption": 0.02,
       |			"threshold": 0.02
       |		},
       |		"reliefDetails": {
       |			"reliefs": true,
       |			"privateResRelief": 0.02,
       |			"lettingsReflief": 0.02,
       |			"giftHoldOverRelief": 0.02,
       |			"otherRelief": " ",
       |			"otherReliefAmount": 0.02
       |		},
       |		"bankDetails": {
       |			"ukBankDetails": {
       |				"accountName": "M Stewart ",
       |				"sortcode": "404450",
       |				"accountNumber": "22599765",
       |				"bankName": "HSBC UK"
       |			}
       |		}
       |	}
       |}
       |""".stripMargin)

  val connector = new SubmitReturnsConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  "SubmitReturnsConnectorImpl" when {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders = Map("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    def expectedSubmitReturnUrl(cgtReference: String) =
      s"http://localhhost:7022/returns/cgt-reference/${cgtReference}/return"

    "handling request to submit return" must {
//      val submitReturnRequest = SubmitReturnRequest(
//
//      )

      "do a post http call and get the result" in {
        List(
          HttpResponse(200),
          HttpResponse(500)
        ).foreach { httpResponse =>

          withClue(s"For http response [${httpResponse.toString}]") {
            mockPost(expectedSubmitReturnUrl("XLCGTP212487578"), expectedHeaders, desSubmitReturnRequest)(
              Some(httpResponse)
            )

            //await(connector.submit(submitReturnRequest).value) shouldBe Right(httpResponse)
          }

        }

      }

    }

  }
}
