/*
 * Copyright 2019 HM Revenue & Customs
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

///*
// * Copyright 2019 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.cgtpropertydisposals.service
//
//import java.time.LocalDateTime
//
//import cats.data.EitherT
//import org.scalamock.scalatest.MockFactory
//import org.scalatest.{Matchers, WordSpec}
//import uk.gov.hmrc.cgtpropertydisposals.TestSupport
//import uk.gov.hmrc.cgtpropertydisposals.models.{Enrolments, Error, KeyValuePair, TaxEnrolmentRequest}
//import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
//
//import scala.concurrent.Future
//class TaxEnrolmentRetryRequestImplSpec extends WordSpec with Matchers with MockFactory with TestSupport {
//
//  val mockRepo = mock[TaxEnrolmentRetryRepository]
//
//  val service = new TaxEnrolmentRetryServiceImpl(mockRepo)
//
//  def mockTaxEnrolmentRetryInsert(
//    taxEnrolmentRetryRequest: TaxEnrolmentRequest
//  )(response: Either[Error, Boolean]) =
//    (mockRepo
//      .insert(_: TaxEnrolmentRequest))
//      .expects(taxEnrolmentRetryRequest)
//      .returning(EitherT(Future.successful(response)))
//
//  def mockTaxEnrolmentRetryCheckExists(
//    userId: String
//  )(response: Either[Error, Option[TaxEnrolmentRequest]]) =
//    (mockRepo
//      .exists(_: String))
//      .expects(userId)
//      .returning(EitherT(Future.successful(response)))
//
//  def mockTaxEnrolmentRetryDelete(
//    userId: String
//  )(response: Either[Error, Int]) =
//    (mockRepo
//      .delete(_: String))
//      .expects(userId)
//      .returning(EitherT(Future.successful(response)))
//
//  "The Tax Enrolment Retry Request Implementation" when {
//    "it receives a request to insert a tax enrolment retry record it" must {
//      "return an error" when {
//        "the database insert fails" in {
//          val taxEnrolmentRetryRequest = TaxEnrolmentRequest(
//            "userId-1",
//            Enrolments(List(KeyValuePair("Postcode", "OK11KO")), List(KeyValuePair("CgtPDRef", "CGT-1"))),
//            "InProgress",
//            LocalDateTime.now()
//          )
//          mockTaxEnrolmentRetryInsert(taxEnrolmentRetryRequest)(Right(false))
//          await(service.insertTaxEnrolmentRequest(taxEnrolmentRetryRequest).value).isLeft shouldBe true
//        }
//        "the database throws an exception" in {
//          val taxEnrolmentRetryRequest = TaxEnrolmentRequest(
//            "userId-1",
//            Enrolments(List(KeyValuePair("Postcode", "OK11KO")), List(KeyValuePair("CgtPDRef", "CGT-1"))),
//            "InProgress",
//            LocalDateTime.now()
//          )
//          mockTaxEnrolmentRetryInsert(taxEnrolmentRetryRequest)(Left(Error("primary node not available")))
//          await(service.insertTaxEnrolmentRequest(taxEnrolmentRetryRequest).value).isLeft shouldBe true
//        }
//      }
//      "return a success" when {
//        "the database insert succeeds" in {
//          val taxEnrolmentRetryRequest = TaxEnrolmentRequest(
//            "userId-1",
//            Enrolments(List(KeyValuePair("Postcode", "OK11KO")), List(KeyValuePair("CgtPDRef", "CGT-1"))),
//            "InProgress",
//            LocalDateTime.now()
//          )
//          mockTaxEnrolmentRetryInsert(taxEnrolmentRetryRequest)(Right(true))
//          await(service.insertTaxEnrolmentRequest(taxEnrolmentRetryRequest).value) shouldBe Right(
//            TaxEnrolmentRequestInProgress
//          )
//        }
//      }
//    }
//
//    "it receives a request to check if a tax enrolment retry record exists for a specific user it" must {
//      "return an error" when {
//        "the database throws an exception" in {
//          mockTaxEnrolmentRetryCheckExists("userId-1")(Left(Error("primary node not available")))
//          await(service.hasTaxEnrolmentRequest("userId-1").value).isLeft shouldBe true
//        }
//      }
//      "return an existence indicator" when {
//        "there exists a record for this user" in {
//          val taxEnrolmentRetryRequest = TaxEnrolmentRequest(
//            "userId-1",
//            Enrolments(List(KeyValuePair("Postcode", "OK11KO")), List(KeyValuePair("CgtPDRef", "CGT-1"))),
//            "InProgress",
//            LocalDateTime.now()
//          )
//          mockTaxEnrolmentRetryCheckExists("userId-1")(Right(Some(taxEnrolmentRetryRequest)))
//          await(service.hasTaxEnrolmentRequest("userId-1").value) shouldBe Right(
//            TaxEnrolmentRetryRequestExists
//          )
//        }
//        "there does not exist a record for this user" in {
//          mockTaxEnrolmentRetryCheckExists("userId-1")(Right(None))
//          await(service.hasTaxEnrolmentRequest("userId-1").value) shouldBe Right(
//            TaxEnrolmentRetryRequestDoesNotExist
//          )
//        }
//      }
//      "it receives a request to delete a tax enrolment retry record it" must {
//        "return an error" when {
//          "the database fails to delete the tax enrolment record" in {
//            mockTaxEnrolmentRetryDelete("userId-1")(Right(0))
//            await(service.deleteTaxEnrolmentRequest("userId-1").value).isLeft shouldBe true
//          }
//          "the database throws an exception" in {
//            mockTaxEnrolmentRetryDelete("userId-1")(Left(Error("primary node not available")))
//            await(service.deleteTaxEnrolmentRequest("userId-1").value).isLeft shouldBe true
//          }
//        }
//        "returns" when {
//          "a tax-enrolment-retry-request-removed response when there exists a record for this user" in {
//            mockTaxEnrolmentRetryDelete("userId-1")(Right(1))
//            await(service.deleteTaxEnrolmentRequest("userId-1").value) shouldBe Right(
//              TaxEnrolmentRetryRequestRemoved
//            )
//          }
//        }
//      }
//    }
//  }
//}
