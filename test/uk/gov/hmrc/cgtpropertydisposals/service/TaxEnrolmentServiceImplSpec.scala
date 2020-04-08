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

package uk.gov.hmrc.cgtpropertydisposals.service
import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.{SubscribedDetails, SubscribedUpdateDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, TelephoneNumber}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.{TaxEnrolmentRepository, VerifiersRepository}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector: TaxEnrolmentConnector            = mock[TaxEnrolmentConnector]
  val mockEnrolmentRepository: TaxEnrolmentRepository = mock[TaxEnrolmentRepository]
  val mockVerifierRepository: VerifiersRepository     = mock[VerifiersRepository]

  val service =
    new TaxEnrolmentServiceImpl(mockConnector, mockEnrolmentRepository, mockVerifierRepository, MockMetrics.metrics)

  def mockAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, HttpResponse]
  ) =
    (mockConnector
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT[Future, Error, HttpResponse](Future.successful(response)))

  def mockUpdateVerifier(updateVerifierDetails: UpdateVerifiersRequest)(
    response: Either[Error, HttpResponse]
  ) =
    (mockConnector
      .updateVerifiers(_: UpdateVerifiersRequest)(_: HeaderCarrier))
      .expects(updateVerifierDetails, *)
      .returning(EitherT[Future, Error, HttpResponse](Future.successful(response)))

  def mockInsertEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Unit]
  ) =
    (mockEnrolmentRepository
      .save(_: TaxEnrolmentRequest))
      .expects(taxEnrolmentRequest)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockInsertVerifier(updateVerifierDetails: UpdateVerifiersRequest)(
    response: Either[Error, Unit]
  ) =
    (mockVerifierRepository
      .insert(_: UpdateVerifiersRequest))
      .expects(updateVerifierDetails)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockGetEnrolment(ggCredId: String)(
    response: Either[Error, Option[TaxEnrolmentRequest]]
  ) =
    (mockEnrolmentRepository
      .get(_: String))
      .expects(ggCredId)
      .returning(EitherT[Future, Error, Option[TaxEnrolmentRequest]](Future.successful(response)))

  def mockGetUpdateVerifier(ggCredId: String)(
    response: Either[Error, Option[UpdateVerifiersRequest]]
  ) =
    (mockVerifierRepository
      .get(_: String))
      .expects(ggCredId)
      .returning(EitherT[Future, Error, Option[UpdateVerifiersRequest]](Future.successful(response)))

  def mockDeleteEnrolment(ggCredId: String)(
    response: Either[Error, Int]
  ) =
    (mockEnrolmentRepository
      .delete(_: String))
      .expects(ggCredId)
      .returning(EitherT[Future, Error, Int](Future.successful(response)))

  def mockDeleteVerifier(ggCredId: String)(
    response: Either[Error, Int]
  ) =
    (mockVerifierRepository
      .delete(_: String))
      .expects(ggCredId)
      .returning(EitherT[Future, Error, Int](Future.successful(response)))

  def mockUpdateEnrolment(ggCredId: String, taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Option[TaxEnrolmentRequest]]
  ) =
    (mockEnrolmentRepository
      .update(_: String, _: TaxEnrolmentRequest))
      .expects(ggCredId, taxEnrolmentRequest)
      .returning(EitherT[Future, Error, Option[TaxEnrolmentRequest]](Future.successful(response)))

  val (nonUkCountry, nonUkCountryCode) = Country("HK", Some("Hong Kong")) -> "HK"
  implicit val hc: HeaderCarrier       = HeaderCarrier()
  val cgtReference                     = "XACGTP123456789"

  val taxEnrolmentRequestWithNonUkAddress = TaxEnrolmentRequest(
    "ggCredId",
    cgtReference,
    Address.NonUkAddress("line1", None, None, None, Some(Postcode("OK11KO")), nonUkCountry)
  )

  val taxEnrolmentRequestWithUkAddress = TaxEnrolmentRequest(
    "ggCredId",
    cgtReference,
    Address.UkAddress("line1", None, None, None, Postcode("OK11KO"))
  )

  val noAddressChange = UpdateVerifiersRequest(
    "ggCredId",
    SubscribedUpdateDetails(
      SubscribedDetails(
        Right(IndividualName("Stephen", "Wood")),
        Email("stephen@abc.co.uk"),
        UkAddress(
          "100 Sutton Street",
          Some("Wokingham"),
          Some("Surrey"),
          Some("London"),
          Postcode("DH14EJ")
        ),
        ContactName("Stephen Wood"),
        CgtReference(cgtReference),
        Some(TelephoneNumber("(+013)32752856")),
        true
      ),
      SubscribedDetails(
        Right(IndividualName("Stephen", "Wood")),
        Email("stephen@abc.co.uk"),
        UkAddress(
          "100 Sutton Street",
          Some("Wokingham"),
          Some("Surrey"),
          Some("London"),
          Postcode("DH14EJ")
        ),
        ContactName("Stephen Wood"),
        CgtReference(cgtReference),
        Some(TelephoneNumber("(+013)32752856")),
        true
      )
    )
  )

  val addressChange = UpdateVerifiersRequest(
    "ggCredId",
    SubscribedUpdateDetails(
      SubscribedDetails(
        Right(IndividualName("Stephen", "Wood")),
        Email("stephen@abc.co.uk"),
        UkAddress(
          "100 Sutton Street",
          Some("Wokingham"),
          Some("Surrey"),
          Some("London"),
          Postcode("DH14EJ")
        ),
        ContactName("Stephen Wood"),
        CgtReference(cgtReference),
        Some(TelephoneNumber("(+013)32752856")),
        true
      ),
      SubscribedDetails(
        Right(IndividualName("Stephen", "Wood")),
        Email("stephen@abc.co.uk"),
        UkAddress(
          "100 Sutton Street",
          Some("Wokingham"),
          Some("Surrey"),
          Some("London"),
          Postcode("BN114JB")
        ),
        ContactName("Stephen Wood"),
        CgtReference(cgtReference),
        Some(TelephoneNumber("(+013)32752856")),
        true
      )
    )
  )

  "TaxEnrolment Service Implementation" when {

    "it receives a request to check if a user has a CGT enrolment" must {

      "return an error" when {

        "there is a mongo exception when calling the tax enrolment repo" in {
          mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Left(Error("Connection error")))
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isLeft shouldBe true
        }

        "there is a mongo exception when calling the verifiers repo" in {
          mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
          mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Left(Error("Connection error")))
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isLeft shouldBe true
        }

      }

      "it receives a request to update the verifiers" must {

        "not make the any enrolment API calls when there is no address change" in {
          await(service.updateVerifiers(noAddressChange).value) shouldBe Right(())
        }

        "make the ES6 call when there is an address change and there does not exists a failed enrolment create request" in {
          inSequence {
            mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
            mockInsertVerifier(addressChange)(Right(()))
            mockGetEnrolment(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(None))
            mockUpdateVerifier(addressChange)(Right(HttpResponse(204)))
            mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
          }
          await(service.updateVerifiers(addressChange).value) shouldBe Right(())
        }

        "make the ES6 call and it fails" in {

          inSequence {
            mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
            mockInsertVerifier(addressChange)(Right(()))
            mockGetEnrolment(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(None))
            mockUpdateVerifier(addressChange)(Right(HttpResponse(500)))
          }
          await(service.updateVerifiers(addressChange).value) shouldBe Right(())
        }

        "make the ES8 call when there is an address change and there exists a failed enrolment create request" in {

          inSequence {
            mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
            mockInsertVerifier(addressChange)(Right(()))
            mockGetEnrolment(taxEnrolmentRequestWithUkAddress.ggCredId)(
              Right(Some(taxEnrolmentRequestWithUkAddress))
            )
            mockUpdateEnrolment(
              taxEnrolmentRequestWithUkAddress.ggCredId,
              taxEnrolmentRequestWithUkAddress.copy(address = addressChange.subscribedUpdateDetails.newDetails.address)
            )(Right(Some(taxEnrolmentRequestWithUkAddress)))

            mockAllocateEnrolment(
              taxEnrolmentRequestWithUkAddress.copy(address = addressChange.subscribedUpdateDetails.newDetails.address)
            )(
              Right(HttpResponse(204))
            )
            mockDeleteEnrolment(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
            mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
          }
          await(service.updateVerifiers(addressChange).value) shouldBe Right(())
        }

      }

      "return a user's subscription status" when {

        "there does not exist a stored enrolment request" in {
          mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
          mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value) shouldBe Right(None)
        }

        "there does exist a stored enrolment request but the enrolment call fails again" in {
          inSequence {
            mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(
              Right(Some(taxEnrolmentRequestWithNonUkAddress))
            )
            mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection error")))
          }
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value) shouldBe Right(
            Some(taxEnrolmentRequestWithNonUkAddress)
          )
        }

        "there does exist a stored enrolment request and the enrolment call succeeds but the deleting of the record fails" in {
          inSequence {
            mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(
              Right(Some(taxEnrolmentRequestWithNonUkAddress))
            )
            mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204)))
            mockDeleteEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Left(Error("Database error")))
          }
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value) shouldBe Right(
            Some(taxEnrolmentRequestWithNonUkAddress)
          )
        }
      }

      "return true" when {

        "an enrolment record exists, the tax enrolment call is made successfully, and the delete occurred correctly" in {
          inSequence {
            mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(
              Right(Some(taxEnrolmentRequestWithNonUkAddress))
            )
            mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204)))
            mockDeleteEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(1))
          }
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isRight shouldBe true
        }

      }

    }

    "it receives a request to allocate an enrolment" must {

      "return an error" when {
        "the http call comes back with a status other than 204 and the recording of the enrolment request fails" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401)))
            mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection Error")))
          }
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isLeft shouldBe true
        }
        "the http call comes back with an exception and the insert into mongo fails" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection error")))
            mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection Error")))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isLeft shouldBe true
        }
      }

      "return a tax enrolment created success response" when {
        "the http call comes back with a status other than 204" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401)))
            mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(()))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }
        "the http call comes back with a status other than no content and the insert into mongo succeeds" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401)))
            mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(()))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }

        "the http call fails and the insert into mongo succeeds" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("uh oh")))
            mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(()))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }

        "the http call comes back with a status of no content and the address is a UK address with a postcode" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithUkAddress)(Right(HttpResponse(204)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithUkAddress).value).isRight shouldBe true
        }
        "the http call comes back with a status of no content and the address is a Non-UK address with a country code" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }
      }
    }
  }
}
