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

import akka.util.ByteString
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{DmsMetadata, DmsSubmissionPayload, FileAttachment}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.subscription.SubscriptionDetails
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.subscription.SubscriptionResponse.SubscriptionSuccessful
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{FileDescriptor, UpscanCallBack, UpscanFileDescriptor}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest

import scala.reflect.{ClassTag, classTag}

object Generators
    extends GenUtils
    with IdGen
    with NameGen
    with OnboardingGen
    with BusinessPartnerRecordGen
    with TaxEnrolmentGen
    with DraftReturnGen
    with UpscanGen
    with DmsSubmissionGen {

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

  implicit val byteStringArb: Arbitrary[ByteString] =
    Arbitrary(
      Gen
        .choose(0L, Long.MaxValue)
        .map(s => ByteString(s))
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

trait UpscanGen { this: GenUtils =>

  implicit val upscanMetaGen: Gen[FileDescriptor]         = gen[FileDescriptor]
  implicit val upscanUploadGen: Gen[UpscanFileDescriptor] = gen[UpscanFileDescriptor]
  implicit val upscanCallBackGen: Gen[UpscanCallBack]     = gen[UpscanCallBack]

}

trait DmsSubmissionGen { this: GenUtils =>
  implicit val dmsMetadataGen: Gen[DmsMetadata]                   = gen[DmsMetadata]
  implicit val fileAttachmentGen: Gen[FileAttachment]             = gen[FileAttachment]
  implicit val dmsSubmissionPayloadGen: Gen[DmsSubmissionPayload] = gen[DmsSubmissionPayload]
}
