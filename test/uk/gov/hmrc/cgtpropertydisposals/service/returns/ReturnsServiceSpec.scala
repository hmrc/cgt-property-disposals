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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.ReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.address.Postcode
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{Charge, ReturnSummary, SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsServiceSpec extends WordSpec with Matchers with MockFactory {

  val returnsConnector = mock[ReturnsConnector]

  val config = Configuration(
    ConfigFactory.parseString(
      """
        |des.non-iso-country-codes = []
        |""".stripMargin
    )
  )

  val returnsService = new DefaultReturnsService(returnsConnector, config, MockMetrics.metrics)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def mockSubmitReturn(returnRequest: SubmitReturnRequest)(response: Either[Error, HttpResponse]) =
    (returnsConnector
      .submit(_: SubmitReturnRequest)(_: HeaderCarrier))
      .expects(returnRequest, hc)
      .returning(EitherT.fromEither[Future](response))

  def mockListReturn(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    response: Either[Error, HttpResponse]
  ) =
    (returnsConnector
      .listReturns(_: CgtReference, _: LocalDate, _: LocalDate)(_: HeaderCarrier))
      .expects(cgtReference, fromDate, toDate, *)
      .returning(EitherT.fromEither[Future](response))

  def mockDisplayReturn(cgtReference: CgtReference, submissionId: String)(response: Either[Error, HttpResponse]) =
    (returnsConnector
      .displayReturn(_: CgtReference, _: String)(_: HeaderCarrier))
      .expects(cgtReference, submissionId, *)
      .returning(EitherT.fromEither[Future](response))

  "CompleteReturnsService" when {

    "handling submitting returns" should {

      "handle successful submits" when {

        "there is a positive charge" in {
          val jsonBody =
            """
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "chargeType": "Late Penalty",
              |     "chargeReference":"XCRG9448959757",
              |     "amount":11.0,
              |     "dueDate":"2020-03-11",
              |     "formBundleNumber":"804123737752",
              |     "cgtReferenceNumber":"XLCGTP212487578"
              |  }
              |}
              |""".stripMargin

          val submitReturnResponse = SubmitReturnResponse(
            "804123737752",
            Some(
              Charge(
                "charge from return submission",
                "XCRG9448959757",
                AmountInPence(1100L),
                LocalDate.of(2020, 3, 11)
              )
            )
          )
          val submitReturnRequest = sample[SubmitReturnRequest]
          mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }

        "there is a negative charge" in {
          val jsonBody =
            """
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "chargeType": "Late Penalty",
              |     "chargeReference":"XCRG9448959757",
              |     "amount":-11.0,
              |     "dueDate":"2020-03-11",
              |     "formBundleNumber":"804123737752",
              |     "cgtReferenceNumber":"XLCGTP212487578"
              |  }
              |}
              |""".stripMargin

          val submitReturnResponse = SubmitReturnResponse(
            "804123737752",
            None
          )
          val submitReturnRequest = sample[SubmitReturnRequest]
          mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }

        "there is a no charge data" in {
          val jsonBody =
            """
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "formBundleNumber":"804123737752",
              |     "cgtReferenceNumber":"XLCGTP212487578"
              |  }
              |}
              |""".stripMargin

          val submitReturnResponse = SubmitReturnResponse("804123737752", None)
          val submitReturnRequest  = sample[SubmitReturnRequest]
          mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }

        "there is a zero charge" in {
          val jsonBody =
            """
              |{
              |"processingDate":"2020-02-20T09:30:47Z",
              |"ppdReturnResponseDetails": {
              |     "formBundleNumber":"804123737752",
              |     "cgtReferenceNumber":"XLCGTP212487578",
              |     "amount" : 0
              |  }
              |}
              |""".stripMargin

          val submitReturnResponse = SubmitReturnResponse("804123737752", None)
          val submitReturnRequest  = sample[SubmitReturnRequest]
          mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
          await(returnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
        }
      }

      "return an error" when {

        "there are charge details for a non zero charge amount and " when {

          def test(jsonResponseBody: JsValue): Unit = {
            val submitReturnRequest = sample[SubmitReturnRequest]
            mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(200, Some(jsonResponseBody))))
            await(returnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
          }
          "the charge amount is missing" in {
            test(
              Json.parse(
                """{
                  |"processingDate":"2020-02-20T09:30:47Z",
                  |"ppdReturnResponseDetails": {
                  |     "chargeType": "Late Penalty",
                  |     "chargeReference":"XCRG9448959757",
                  |     "dueDate":"2020-03-11",
                  |     "formBundleNumber":"804123737752",
                  |     "cgtReferenceNumber":"XLCGTP212487578"
                  |  }
                  |}
                  |""".stripMargin
              )
            )
          }

          "the charge reference is missing" in {
            test(
              Json.parse(
                """{
                  |"processingDate":"2020-02-20T09:30:47Z",
                  |"ppdReturnResponseDetails": {
                  |     "chargeType": "Late Penalty",
                  |     "amount":11.0,
                  |     "dueDate":"2020-03-11",
                  |     "formBundleNumber":"804123737752",
                  |     "cgtReferenceNumber":"XLCGTP212487578"
                  |  }
                  |}
                  |""".stripMargin
              )
            )
          }

          "the charge due date is missing" in {
            test(
              Json.parse(
                """{
                  |"processingDate":"2020-02-20T09:30:47Z",
                  |"ppdReturnResponseDetails": {
                  |     "chargeType": "Late Penalty",
                  |     "chargeReference":"XCRG9448959757",
                  |     "amount":11.0,
                  |     "formBundleNumber":"804123737752",
                  |     "cgtReferenceNumber":"XLCGTP212487578"
                  |  }
                  |}
                  |""".stripMargin
              )
            )
          }

        }

        "the call to submit a return fails" in {
          val submitReturnRequest = sample[SubmitReturnRequest]
          mockSubmitReturn(submitReturnRequest)(
            Left(Error("oh no!"))
          )
          await(returnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
        }

        "the http call comes back with a status other than 200" in {
          val submitReturnRequest = sample[SubmitReturnRequest]
          mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(500)))
          await(returnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
        }
      }
    }

    "handling requests to list returns" must {

      def desResponseBody(countryCode: String) = Json.parse(
        s"""
          |{
          |    "processingDate": "2018-11-06T09:30:47Z",
          |    "returnList": [
          |        {
          |            "submissionId": "09765432111",
          |            "submissionDate": "2018-04-05",
          |            "completionDate": "2018-07-31",
          |            "lastUpdatedDate": "2018-08-31",
          |            "taxYear": "2018",
          |            "status": "aaaaaaaaaaaa",
          |            "totalCGTLiability": 12345678912.12,
          |            "totalOutstanding": 12345678913.12,
          |            "charges": [
          |                {
          |                	"chargeDescription": "Late Payment",
          |                    "chargeAmount": 12345678914.12,
          |                    "dueDate": "2018-08-13",
          |                    "chargeReference": "XDCGTX100004"
          |                },
          |                {
          |                	"chargeDescription": "Interest",
          |                    "chargeAmount": 12345678915.12,
          |                    "dueDate": "2018-09-10",
          |                    "chargeReference": "XDCGTX100005"
          |                }
          |            ],
          |            "propertyAddress": {
          |                "addressLine1": "AddrLine1",
          |                "addressLine2": "AddrLine2",
          |                "addressLine3": "AddrLine3",
          |                "addressLine4": "AddrLine4",
          |                "countryCode": "$countryCode",
          |                "postalCode": "TF3 4ER"
          |            }
          |        },
          |        {
          |            "submissionId": "09765432112",
          |            "submissionDate": "2018-04-05",
          |            "completionDate": "2018-07-31",
          |            "lastUpdatedDate": "2018-08-31",
          |            "taxYear": "2018",
          |            "status": "aaaaaaaaaaaa",
          |            "totalCGTLiability": 12345678955.12,
          |            "totalOutstanding": 45678913.12,
          |            "charges": [
          |                {
          |                	"chargeDescription": "Surcharges",
          |                    "chargeAmount": 12345678914.12,
          |                    "dueDate": "2018-08-13",
          |                    "chargeReference": "XDCGTX100006"
          |                },
          |                {
          |                	"chargeDescription": "Late Payment",
          |                    "chargeAmount": 12345678915.12,
          |                    "dueDate": "2018-09-10",
          |                    "chargeReference": "XDCGTX100007"
          |                }
          |            ],
          |            "propertyAddress": {
          |                "addressLine1": "AddrLine1",
          |                "addressLine2": "AddrLine2",
          |                "addressLine3": "AddrLine3",
          |                "addressLine4": "AddrLine4",
          |                "countryCode": "$countryCode",
          |                "postalCode": "TF3 4ER"
          |            }
          |        }
          |    ]
          |}
          |""".stripMargin
      )

      val expectedReturns = List(
        ReturnSummary(
          "09765432111",
          LocalDate.of(2018, 4, 5),
          LocalDate.of(2018, 7, 31),
          Some(LocalDate.of(2018, 8, 31)),
          "2018",
          AmountInPence(1234567891212L),
          AmountInPence(1234567891312L),
          UkAddress(
            "AddrLine1",
            Some("AddrLine2"),
            Some("AddrLine3"),
            Some("AddrLine4"),
            Postcode("TF3 4ER")
          ),
          List(
            Charge(
              "Late Payment",
              "XDCGTX100004",
              AmountInPence(1234567891412L),
              LocalDate.of(2018, 8, 13)
            ),
            Charge(
              "Interest",
              "XDCGTX100005",
              AmountInPence(1234567891512L),
              LocalDate.of(2018, 9, 10)
            )
          )
        ),
        ReturnSummary(
          "09765432112",
          LocalDate.of(2018, 4, 5),
          LocalDate.of(2018, 7, 31),
          Some(LocalDate.of(2018, 8, 31)),
          "2018",
          AmountInPence(1234567895512L),
          AmountInPence(4567891312L),
          UkAddress(
            "AddrLine1",
            Some("AddrLine2"),
            Some("AddrLine3"),
            Some("AddrLine4"),
            Postcode("TF3 4ER")
          ),
          List(
            Charge(
              "Surcharges",
              "XDCGTX100006",
              AmountInPence(1234567891412L),
              LocalDate.of(2018, 8, 13)
            ),
            Charge(
              "Late Payment",
              "XDCGTX100007",
              AmountInPence(1234567891512L),
              LocalDate.of(2018, 9, 10)
            )
          )
        )
      )

      val cgtReference       = sample[CgtReference]
      val (fromDate, toDate) = LocalDate.now().minusDays(1L) -> LocalDate.now()

      "return an error " when {

        "the http call fails" in {
          mockListReturn(cgtReference, fromDate, toDate)(Left(Error("")))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the http call returns with a status which is not 200" in {
          mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(404)))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the response body cannot be parsed" in {
          mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(JsString("Hi!")))))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the address in a return is a non uk address" in {
          mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desResponseBody("HK")))))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

      }

      "return a list of returns" when {

        "the response body can be parsed and converted" in {
          mockListReturn(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(desResponseBody("GB")))))

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(expectedReturns)

        }

      }

      "return an empty list of returns" when {

        "the response comes back with status 404 and a single error in the body" in {
          mockListReturn(cgtReference, fromDate, toDate)(
            Right(
              HttpResponse(
                404,
                Some(Json.parse("""
              |{
              |  "code" : "NOT_FOUND",
              |  "reason" : "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
              |}
              |""".stripMargin))
              )
            )
          )

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)

        }

        "the response comes back with status 404 and multiple error in the body" in {
          mockListReturn(cgtReference, fromDate, toDate)(
            Right(
              HttpResponse(
                404,
                Some(Json.parse("""
                                  |{
                                  |  "failures" : [ 
                                  |    {
                                  |      "code" : "NOT_FOUND",
                                  |      "reason" : "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
                                  |    }
                                  |  ]
                                  |}  
                                  |""".stripMargin))
              )
            )
          )

          await(returnsService.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(List.empty)

        }

      }

    }

    "handling requests to display a return" must {

      val desResponseBody = Json.parse(
        s"""
           |{
           |	"returnType": {
           |		"source": "Agent",
           |		"submissionType": "New",
           |		"submissionDate": "2009-08-13"
           |	},
           |	"returnDetails": {
           |		"customerType": "Individual",
           |		"completionDate": "2009-09-13",
           |		"isUKResident": true,
           |		"countryResidence": "France",
           |		"numberDisposals": 3,
           |		"totalTaxableGain": 12345678912.12,
           |		"totalNetLoss": 12345678913.12,
           |		"valueAtTaxBandDetails": [
           |			{
           |				"taxRate": 18.02,
           |				"valueAtTaxRate": 12345678914.12
           |			},
           |			{
           |				"taxRate": 19.02,
           |				"valueAtTaxRate": 12345678915.12
           |			}
           |		],
           |		"totalLiability": 12345678916.12,
           |		"adjustedAmount": 12345678917.12,
           |		"totalYTDLiability": 12345678918.12,
           |		"entrepreneursRelief": 12345678919.12,
           |		"estimate": true,
           |		"repayment": true,
           |		"attachmentUpload": true,
           |		"attachmentID": "123456789",
           |		"declaration": true
           |	},
           |	"representedPersonDetails": {
           |		"capacitorPersonalRep": "Personal Representative",
           |		"firstName": "John",
           |		"lastName": "Matt",
           |		"idType": "NINO",
           |		"idValue": "SZ1235797",
           |		"dateOfDeath": "2015-08-13",
           |		"trustCessationDate": "2016-03-13",
           |		"trustTerminationDate": "2015-07-13",
           |		"addressDetails": {
           |			"addressLine1": "addressLine1",
           |			"addressLine2": "addressLine2",
           |			"addressLine3": "addressLine3",
           |			"addressLine4": "addressLine4",
           |			"countryCode": "GB",
           |			"postalCode": "TF34ER"
           |		},
           |		"email": "abc@email.com"
           |	},
           |	"disposalDetails": [		
           |		{
           |			"disposalDate": "2016-03-13",
           |			"addressDetails": {
           |				"addressLine1": "DisAddressLine11",
           |				"addressLine2": "DisAddressLine22",
           |				"addressLine3": "DisAddressLine33",
           |				"addressLine4": "DisAddressLine43",
           |				"countryCode": "GB",
           |				"postalCode": "TF34NT"
           |			},
           |			"assetType": "Residential",
           |			"percentOwned": 99.00,
           |			"acquisitionType": "Bought",
           |			"acquiredDate": "2017-06-13",
           |			"landRegistry": true,
           |			"acquisitionPrice": 12345678916.13,
           |			"rebased": true,
           |			"rebasedAmount": 12345678917.14,
           |			"disposalType": "Cash",
           |			"disposalPrice": 12345678918.15,
           |			"improvements": true,
           |			"improvementCosts": 12345678919.16,
           |			"acquisitionFees": 12345678920.17,
           |			"disposalFees": 12345678921.18,
           |			"initialGain": 12345678922.19,
           |			"initialLoss": 12345678923.20
           |		}
           |	],
           |	"lossSummaryDetails": {
           |		"inYearLoss": true,
           |		"inYearLossUsed": 12345678923.12,
           |		"preYearLoss": true,
           |		"preYearLossUsed": 12345678925.12
           |	},
           |	"incomeAllowanceDetails": {
           |		"estimatedIncome": 12345678926.12,
           |		"personalAllowance": 12345678927.12,
           |		"annualExemption": 12345678928.12,
           |		"threshold": 12345678929.12
           |	},
           |	"reliefDetails": {
           |		"reliefs": true,
           |		"privateResRelief": 12345678935.12,
           |		"lettingsRelief": 12345678934.12,
           |		"giftHoldOverRelief": 12345678933.12,
           |		"otherRelief": "Tax Relief",
           |		"otherReliefAmount": 12345678932.12
           |	}
           |}
           |""".stripMargin
      )

      val cgtReference = sample[CgtReference]
      val submissionId = "id"

      "return an error " when {

        "the http call fails" in {
          mockDisplayReturn(cgtReference, submissionId)(Left(Error("")))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

        "the http call returns with a status which is not 200" in {
          mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(500)))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

        "there is no response body" in {
          mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(200)))

          await(returnsService.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
        }

      }

      "return a list of returns" when {

        "the response body can be parsed and converted" in {
          mockDisplayReturn(cgtReference, submissionId)(Right(HttpResponse(200, Some(desResponseBody))))

          await(returnsService.displayReturn(cgtReference, submissionId).value) shouldBe Right(desResponseBody)
        }

      }

    }

  }
}
