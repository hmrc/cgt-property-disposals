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

package uk.gov.hmrc.cgtpropertydisposals.models

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}

import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionDetails
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.SubscriptionSuccessful
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CompleteYearToDateLiabilityAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import org.scalacheck.ScalacheckShapeless._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers

import scala.reflect.{ClassTag, classTag}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.des.homepage.FinancialDataRequest
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers

object Generators
    extends GenUtils
    with IdGen
    with NameGen
    with OnboardingGen
    with BusinessPartnerRecordGen
    with TaxEnrolmentGen
    with DraftReturnGen
    with ReturnsGen
    with AddressGen
    with FinancialDataRequestGen {

  def sample[A: ClassTag](implicit gen: Gen[A]): A =
    gen.sample.getOrElse(sys.error(s"Could not generate instance of ${classTag[A].runtimeClass.getSimpleName}"))

  implicit def arb[A](implicit g: Gen[A]): Arbitrary[A] = Arbitrary(g)

}

sealed trait GenUtils {

  def gen[A](implicit arb: Arbitrary[A]): Gen[A] = arb.arbitrary

  // define our own Arbitrary instance for String to generate more legible strings
  implicit val stringArb: Arbitrary[String] = Arbitrary(
    for {
      n <- Gen.choose(1, 30)
      s <- Gen.listOfN(n, Gen.alphaChar).map(_.mkString(""))
    } yield s
  )

  implicit val longArb: Arbitrary[Long] = Arbitrary(Gen.choose(0L, 100L))

  implicit val bigDecimalGen: Arbitrary[BigDecimal] = Arbitrary(Gen.choose(0, 100).map(BigDecimal(_)))

  implicit val localDateTimeArb: Arbitrary[LocalDateTime] =
    Arbitrary(
      Gen
        .chooseNum(0L, 10000L)
        .map(l => LocalDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault()))
    )

  implicit val localDateArb: Arbitrary[LocalDate] = Arbitrary(
    Gen.chooseNum(0, 10000L).map(LocalDate.ofEpochDay(_))
  )

}

trait IdGen { this: GenUtils =>

  implicit val cgtReferenceGen: Gen[CgtReference] = gen[CgtReference]

  implicit val sapNumberGen: Gen[SapNumber] = gen[SapNumber]

}

trait NameGen { this: GenUtils =>

  implicit val individualNameGen: Gen[IndividualName] = gen[IndividualName]

  implicit val trustNameGen: Gen[TrustName] = gen[TrustName]

}

trait OnboardingGen { this: GenUtils =>

  implicit val registrationDetailsGen: Gen[RegistrationDetails] = gen[RegistrationDetails]

  implicit val subscriptionDetailsGen: Gen[SubscriptionDetails] = gen[SubscriptionDetails]

  implicit val subscriptionSuccessfulGen: Gen[SubscriptionSuccessful] = gen[SubscriptionSuccessful]

}

trait BusinessPartnerRecordGen { this: GenUtils =>

  implicit val bprGen: Gen[BusinessPartnerRecord] = gen[BusinessPartnerRecord]

  implicit val bprRequestGen: Gen[BusinessPartnerRecordRequest] = gen[BusinessPartnerRecordRequest]

}

trait TaxEnrolmentGen { this: GenUtils =>

  implicit val taxEnrolmentRequestGen: Gen[TaxEnrolmentRequest] = gen[TaxEnrolmentRequest]

  implicit val updateVerifiersRequestGen: Gen[UpdateVerifiersRequest] = gen[UpdateVerifiersRequest]

}

trait DraftReturnGen { this: GenUtils =>

  implicit val draftReturnGen: Gen[DraftReturn] = gen[DraftReturn]

}

trait ReturnsGen { this: GenUtils =>

  implicit val completeReturnGen: Gen[CompleteReturn] = gen[CompleteReturn]

  implicit val completeSingleDisposalTriageAnswersGen: Gen[CompleteSingleDisposalTriageAnswers] =
    gen[CompleteSingleDisposalTriageAnswers]

  implicit val completeYearToDateLiabilityAnswersGen: Gen[CompleteYearToDateLiabilityAnswers] =
    gen[CompleteYearToDateLiabilityAnswers]

  implicit val hasEstimatedDetailsWithCalculatedTaxDueGen: Gen[HasEstimatedDetailsWithCalculatedTaxDue] =
    gen[HasEstimatedDetailsWithCalculatedTaxDue]

  implicit val calculatedTaxDueGen: Gen[CalculatedTaxDue] = gen[CalculatedTaxDue]

  implicit val nonGainCalculatedTaxDueGen: Gen[NonGainCalculatedTaxDue] = gen[NonGainCalculatedTaxDue]

  implicit val gainCalculatedTaxDueGen: Gen[GainCalculatedTaxDue] = gen[GainCalculatedTaxDue]

  implicit val completeAcquisitionDetailsAnswersGen: Gen[CompleteAcquisitionDetailsAnswers] =
    gen[CompleteAcquisitionDetailsAnswers]

  implicit val completeExemptionAndLossesAnswersGen: Gen[CompleteExemptionAndLossesAnswers] =
    gen[CompleteExemptionAndLossesAnswers]

  implicit val listReturnResponseGen: Gen[ListReturnsResponse] =
    gen[ListReturnsResponse]

  implicit val submitReturnRequestGen: Gen[SubmitReturnRequest] = gen[SubmitReturnRequest]

  implicit val submitReturnResponseGen: Gen[SubmitReturnResponse] = gen[SubmitReturnResponse]

}

trait AddressGen { this: GenUtils =>

  implicit val addressGen: Gen[Address] = gen[Address]

  implicit val postcodeGen: Gen[Postcode] = Gen.oneOf(List(Postcode("BN11 3QY"), Postcode("BN11 4QY")))

  implicit val ukAddressGen: Gen[UkAddress] = {
    for {
      a <- gen[UkAddress]
      p <- postcodeGen
    } yield a.copy(postcode = p)
  }

  implicit val countryGen: Gen[Country] = {
    val countries = Country.countryCodeToCountryName.map { case (code, name) => Country(code, Some(name)) }.toList
    Gen.oneOf(countries)
  }

}

trait AddressLowerPriorityGen { this: GenUtils =>

  implicit val nonUkAddressGen: Gen[NonUkAddress] = gen[NonUkAddress]

}

trait FinancialDataRequestGen { this: GenUtils =>

  implicit val financialDataRequestGen: Gen[FinancialDataRequest] = gen[FinancialDataRequest]

}
