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
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers

import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers

import scala.reflect.{ClassTag, classTag}

object Generators
    extends GenUtils
    with IdGen
    with NameGen
    with OnboardingGen
    with BusinessPartnerRecordGen
    with TaxEnrolmentGen
    with DraftReturnGen
    with SubmitReturnRequestGen
    with CompleteReturnGen
    with CompleteTriageAnswersGen
    with CompleteYearToDateLiabilityAnswersGen
    with HasEstimatedDetailsWithCalculatedTaxDueGen
    with CalculatedTaxDueGen
    with GainCalculatedTaxDueGen
    with NonGainCalculatedTaxDueGen
    with CompleteAcquisitionDetailsAnswersGen
    with CompleteExemptionAndLossesAnswersGen {

  def sample[A: ClassTag](implicit gen: Gen[A]): A =
    gen.sample.getOrElse(sys.error(s"Could not generate instance of ${classTag[A].runtimeClass.getSimpleName}"))

  implicit def arb[A](implicit g: Gen[A]): Arbitrary[A] = Arbitrary(g)

}

sealed trait GenUtils {

  def gen[A](implicit arb: Arbitrary[A]): Gen[A] = arb.arbitrary

  // define our own Arbitrary instance for String to generate more legible strings
  implicit val stringArb: Arbitrary[String] = Arbitrary(Gen.alphaNumStr)

  implicit val longArb: Arbitrary[Long] = Arbitrary(Gen.choose(-5e13.toLong, 5e13.toLong))

  implicit val bigDecimalGen: Arbitrary[BigDecimal] = Arbitrary(Gen.choose(0L, 1e9.toLong).map(BigDecimal(_)))

  implicit val localDateTimeArb: Arbitrary[LocalDateTime] =
    Arbitrary(
      Gen
        .chooseNum(0L, Long.MaxValue)
        .map(l => LocalDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault()))
    )

  implicit val localDateArb: Arbitrary[LocalDate] = Arbitrary(
    Gen.chooseNum(0, Int.MaxValue).map(LocalDate.ofEpochDay(_))
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

trait SubmitReturnRequestGen { this: GenUtils =>

  implicit val submitReturnRequestGen: Gen[SubmitReturnRequest] = gen[SubmitReturnRequest]

}

trait CompleteReturnGen { this: GenUtils =>

  implicit val completeReturnGen: Gen[CompleteReturn] = gen[CompleteReturn]

}

trait CompleteTriageAnswersGen { this: GenUtils =>

  implicit val completeSingleDisposalTriageAnswersGen: Gen[CompleteSingleDisposalTriageAnswers] =
    gen[CompleteSingleDisposalTriageAnswers]

}

trait CompleteYearToDateLiabilityAnswersGen { this: GenUtils =>

  implicit val completeYearToDateLiabilityAnswersGen: Gen[CompleteYearToDateLiabilityAnswers] =
    gen[CompleteYearToDateLiabilityAnswers]

}

trait HasEstimatedDetailsWithCalculatedTaxDueGen { this: GenUtils =>

  implicit val hasEstimatedDetailsWithCalculatedTaxDueGen: Gen[HasEstimatedDetailsWithCalculatedTaxDue] =
    gen[HasEstimatedDetailsWithCalculatedTaxDue]

}

trait CalculatedTaxDueGen { this: GenUtils =>

  implicit val calculatedTaxDueGen: Gen[CalculatedTaxDue] = gen[CalculatedTaxDue]

}

trait NonGainCalculatedTaxDueGen { this: GenUtils =>

  implicit val nonGainCalculatedTaxDueGen: Gen[NonGainCalculatedTaxDue] = gen[NonGainCalculatedTaxDue]

}

trait GainCalculatedTaxDueGen { this: GenUtils =>

  implicit val gainCalculatedTaxDueGen: Gen[GainCalculatedTaxDue] = gen[GainCalculatedTaxDue]

}

trait CompleteAcquisitionDetailsAnswersGen { this: GenUtils =>

  implicit val completeAcquisitionDetailsAnswersGen: Gen[CompleteAcquisitionDetailsAnswers] = gen[CompleteAcquisitionDetailsAnswers]

}

trait CompleteExemptionAndLossesAnswersGen { this: GenUtils =>

  implicit val completeExemptionAndLossesAnswersGen: Gen[CompleteExemptionAndLossesAnswers] = gen[CompleteExemptionAndLossesAnswers]

}