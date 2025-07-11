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

package uk.gov.hmrc.cgtpropertydisposals.service.enrolments

import cats.data.EitherT
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, TelephoneNumber}
import uk.gov.hmrc.cgtpropertydisposals.repositories.enrolments.{TaxEnrolmentRepository, VerifiersRepository}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxEnrolmentServiceImplSpec extends AnyWordSpec with Matchers with CleanMongoCollectionSupport {

  val mockConnector: TaxEnrolmentConnector            = mock[TaxEnrolmentConnector]
  val mockEnrolmentRepository: TaxEnrolmentRepository = mock[TaxEnrolmentRepository]
  val mockVerifierRepository: VerifiersRepository     = mock[VerifiersRepository]

  val service =
    new TaxEnrolmentServiceImpl(mockConnector, mockEnrolmentRepository, mockVerifierRepository, MockMetrics.metrics)

  private def mockAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, HttpResponse]
  ) =
    when(
      mockConnector
        .allocateEnrolmentToGroup(ArgumentMatchers.eq(taxEnrolmentRequest))(any())
    ).thenReturn(EitherT[Future, Error, HttpResponse](Future.successful(response)))

  private def mockUpdateVerifier(updateVerifierDetails: UpdateVerifiersRequest)(
    response: Either[Error, HttpResponse]
  ) =
    when(
      mockConnector
        .updateVerifiers(ArgumentMatchers.eq(updateVerifierDetails))(any())
    ).thenReturn(EitherT[Future, Error, HttpResponse](Future.successful(response)))

  private def mockInsertEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Unit]
  ) =
    when(
      mockEnrolmentRepository
        .save(taxEnrolmentRequest)
    ).thenReturn(EitherT[Future, Error, Unit](Future.successful(response)))

  private def mockInsertVerifier(updateVerifierDetails: UpdateVerifiersRequest)(
    response: Either[Error, Unit]
  ) =
    when(
      mockVerifierRepository
        .insert(updateVerifierDetails)
    ).thenReturn(EitherT[Future, Error, Unit](Future.successful(response)))

  private def mockGetEnrolment(ggCredId: String)(
    response: Either[Error, Option[TaxEnrolmentRequest]]
  ) =
    when(
      mockEnrolmentRepository
        .get(ggCredId)
    ).thenReturn(EitherT[Future, Error, Option[TaxEnrolmentRequest]](Future.successful(response)))

  private def mockGetUpdateVerifier(ggCredId: String)(
    response: Either[Error, Option[UpdateVerifiersRequest]]
  ) =
    when(
      mockVerifierRepository
        .get(ggCredId)
    ).thenReturn(EitherT[Future, Error, Option[UpdateVerifiersRequest]](Future.successful(response)))

  private def mockDeleteEnrolment(ggCredId: String)(
    response: Either[Error, Int]
  ) =
    when(
      mockEnrolmentRepository
        .delete(ggCredId)
    ).thenReturn(EitherT[Future, Error, Int](Future.successful(response)))

  private def mockDeleteVerifier(ggCredId: String)(
    response: Either[Error, Int]
  ) =
    when(
      mockVerifierRepository
        .delete(ggCredId)
    ).thenReturn(EitherT[Future, Error, Int](Future.successful(response)))

  private def mockUpdateEnrolment(ggCredId: String, taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Option[TaxEnrolmentRequest]]
  ) =
    when(
      mockEnrolmentRepository
        .update(ggCredId, taxEnrolmentRequest)
    ).thenReturn(EitherT[Future, Error, Option[TaxEnrolmentRequest]](Future.successful(response)))

  val (nonUkCountry, nonUkCountryCode) = Country("HK") -> "HK"
  implicit val hc: HeaderCarrier       = HeaderCarrier()
  val cgtReference                     = "XACGTP123456789"

  private val taxEnrolmentRequestWithNonUkAddress = TaxEnrolmentRequest(
    "ggCredId",
    cgtReference,
    Address.NonUkAddress("line1", None, None, None, Some(Postcode("OK11KO")), nonUkCountry)
  )

  private val taxEnrolmentRequestWithUkAddress = TaxEnrolmentRequest(
    "ggCredId",
    cgtReference,
    Address.UkAddress("line1", None, None, None, Postcode("OK11KO"))
  )

  private val noAddressChange = UpdateVerifiersRequest(
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
        registeredWithId = true
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
        registeredWithId = true
      )
    )
  )

  private val addressChange = UpdateVerifiersRequest(
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
        registeredWithId = true
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
        registeredWithId = true
      )
    )
  )

  private val emptyJsonBody = "{}"

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
          mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
          mockInsertVerifier(addressChange)(Right(()))
          mockGetEnrolment(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(None))
          mockUpdateVerifier(addressChange)(Right(HttpResponse(204, emptyJsonBody)))
          mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
          await(service.updateVerifiers(addressChange).value) shouldBe Right(())
        }

        "make the ES6 call and it fails" in {
          mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
          mockInsertVerifier(addressChange)(Right(()))
          mockGetEnrolment(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(None))
          mockUpdateVerifier(addressChange)(Right(HttpResponse(500, emptyJsonBody)))
          await(service.updateVerifiers(addressChange).value) shouldBe Right(())
        }

        "make the ES8 call when there is an address change and there exists a failed enrolment create request" in {
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
            Right(HttpResponse(204, emptyJsonBody))
          )
          mockDeleteEnrolment(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
          mockDeleteVerifier(taxEnrolmentRequestWithUkAddress.ggCredId)(Right(1))
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
          mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(
            Right(Some(taxEnrolmentRequestWithNonUkAddress))
          )
          mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection error")))
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value) shouldBe Right(
            Some(taxEnrolmentRequestWithNonUkAddress)
          )
        }

        "there does exist a stored enrolment request and the enrolment call succeeds but the deleting of the record fails" in {
          mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(
            Right(Some(taxEnrolmentRequestWithNonUkAddress))
          )
          mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204, emptyJsonBody)))
          mockDeleteEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Left(Error("Database error")))
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value) shouldBe Right(
            Some(taxEnrolmentRequestWithNonUkAddress)
          )
        }
      }

      "return true" when {
        "an enrolment record exists, the tax enrolment call is made successfully, and the delete occurred correctly" in {
          mockGetEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(
            Right(Some(taxEnrolmentRequestWithNonUkAddress))
          )
          mockGetUpdateVerifier(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204, emptyJsonBody)))
          mockDeleteEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(1))
          await(service.hasCgtSubscription(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isRight shouldBe true
        }
      }
    }

    "it receives a request to allocate an enrolment" must {
      "return an error" when {
        "the http call comes back with a status other than 204 and the recording of the enrolment request fails" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401, emptyJsonBody)))
          mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection Error")))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isLeft shouldBe true
        }

        "the http call comes back with an exception and the insert into mongo fails" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection error")))
          mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection Error")))

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isLeft shouldBe true
        }
      }

      "return a tax enrolment created success response" when {
        "the http call comes back with a status other than 204" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401, emptyJsonBody)))
          mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(()))

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }

        "the http call comes back with a status other than no content and the insert into mongo succeeds" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401, emptyJsonBody)))
          mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(()))

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }

        "the http call fails and the insert into mongo succeeds" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("uh oh")))
          mockInsertEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(()))

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }

        "the http call comes back with a status of no content and the address is a UK address with a postcode" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithUkAddress)(Right(HttpResponse(204, emptyJsonBody)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithUkAddress).value).isRight shouldBe true
        }

        "the http call comes back with a status of no content and the address is a Non-UK address with a country code" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204, emptyJsonBody)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }
      }
    }
  }
}
