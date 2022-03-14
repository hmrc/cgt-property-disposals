/*
 * Copyright 2022 HM Revenue & Customs
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
import java.util.Base64

import akka.util.ByteString
import org.joda.time.DateTime
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.{Arbitrary, Gen}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails, SingleMixedUseDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.des.{DesFinancialTransaction, DesSubscriptionUpdateRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{B64Html, DmsMetadata, DmsSubmissionPayload, FileAttachment}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.ids._
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.BusinessPartnerRecordRequest.IndividualBusinessPartnerRecordRequest
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.SubscriptionSuccessful
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExampleCompanyDetailsAnswers.CompleteExampleCompanyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MixedUsePropertyDetailsAnswers.CompleteMixedUsePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.OtherReliefsOption.OtherReliefs
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.{CompleteSupportingEvidenceAnswers, SupportingEvidence}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{DraftReturn, TaxableGainOrLossCalculationRequest, _}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.{NewUpscanSuccess, UploadDetails, UpscanSuccess}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UploadReference, UpscanUpload}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionRequest
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService.DesReturnSummary
import uk.gov.hmrc.workitem.WorkItem

import scala.reflect.{ClassTag, classTag}

object Generators
    extends GenUtils
    with IdGen
    with NameGen
    with EmailGen
    with OnboardingGen
    with BusinessPartnerRecordGen
    with TaxEnrolmentGen
    with DraftReturnGen
    with UpscanGen
    with DmsSubmissionGen
    with ReturnsGen
    with AddressGen
    with MoneyGen
    with DesReturnsGen
    with B64HtmlGen
    with FurtherReturnCalculationGen {

  def sample[A : ClassTag](implicit gen: Gen[A]): A =
    gen.sample.getOrElse(sys.error(s"Could not generate instance of ${classTag[A].runtimeClass.getSimpleName}"))

  def sampleOptional[A : ClassTag](implicit gen: Gen[A]): Option[A] =
    Gen
      .option(gen)
      .sample
      .getOrElse(sys.error(s"Could not generate instance of ${classTag[A].runtimeClass.getSimpleName}"))

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

  implicit val jodaDateTime: Arbitrary[DateTime] =
    Arbitrary(
      Gen
        .chooseNum(0L, 10000L)
        .map(l => new DateTime(l))
    )

  implicit val localDateArb: Arbitrary[LocalDate] = Arbitrary(
    Gen.chooseNum(0, 10000L).map(LocalDate.ofEpochDay(_))
  )

  implicit val byteStringArb: Arbitrary[ByteString] =
    Arbitrary(
      Gen
        .choose(0L, Long.MaxValue)
        .map(s => ByteString(s))
    )

  implicit val bsonObjectId: Arbitrary[BSONObjectID] =
    Arbitrary(
      Gen
        .choose(0L, 10000L)
        .map(_ => BSONObjectID.generate())
    )

  implicit val instantArb: Arbitrary[Instant] =
    Arbitrary(
      Gen
        .chooseNum(0L, 10000L)
        .map(l => Instant.ofEpochMilli(l))
    )

}

trait IdGen { this: GenUtils =>

  implicit val cgtReferenceGen: Gen[CgtReference] = gen[CgtReference]

  implicit val sapNumberGen: Gen[SapNumber] = gen[SapNumber]

  implicit val sautrGen: Gen[SAUTR] = gen[SAUTR]

  implicit val ninoGen: Gen[NINO] = gen[NINO]

  implicit val agentReferenceNumberGen: Gen[AgentReferenceNumber] = gen[AgentReferenceNumber]

}

trait B64HtmlGen { this: GenUtils =>
  implicit val b64HtmlGen: Gen[B64Html] =
    Gen.asciiStr.map(s => B64Html(new String(Base64.getEncoder.encode(s.getBytes()))))
}

trait NameGen { this: GenUtils =>

  implicit val individualNameGen: Gen[IndividualName] = gen[IndividualName]

  implicit val trustNameGen: Gen[TrustName] = gen[TrustName]

  implicit val contactNameGen: Gen[ContactName] = gen[ContactName]

}

trait EmailGen { this: GenUtils =>

  implicit val emailGen: Gen[Email] = gen[Email]

}

trait OnboardingGen { this: GenUtils =>

  implicit val registrationDetailsGen: Gen[RegistrationDetails] = gen[RegistrationDetails]

  implicit val subscriptionDetailsGen: Gen[SubscriptionDetails] = gen[SubscriptionDetails]

  implicit val subscriptionSuccessfulGen: Gen[SubscriptionSuccessful] = gen[SubscriptionSuccessful]

  implicit val subscribedDetailsGen: Gen[SubscribedDetails] = gen[SubscribedDetails]

  implicit val desSubscriptionRequestGen: Gen[DesSubscriptionRequest] = gen[DesSubscriptionRequest]

  implicit val desUpdateRequestGen: Gen[DesSubscriptionUpdateRequest] = gen[DesSubscriptionUpdateRequest]

}

trait BusinessPartnerRecordGen { this: GenUtils =>

  implicit val bprGen: Gen[BusinessPartnerRecord] = gen[BusinessPartnerRecord]

  implicit val bprRequestGen: Gen[BusinessPartnerRecordRequest] = gen[BusinessPartnerRecordRequest]

  implicit val individualBprRequestGen: Gen[IndividualBusinessPartnerRecordRequest] =
    gen[IndividualBusinessPartnerRecordRequest]

}

trait TaxEnrolmentGen { this: GenUtils =>

  implicit val taxEnrolmentRequestGen: Gen[TaxEnrolmentRequest] = gen[TaxEnrolmentRequest]

  implicit val updateVerifiersRequestGen: Gen[UpdateVerifiersRequest] = gen[UpdateVerifiersRequest]

}

trait DraftReturnGen extends LowerPriorityDraftReturnGen { this: GenUtils =>

  implicit val draftReturnGen: Gen[DraftReturn] = gen[DraftReturn]

  implicit val singleDisposalDraftReturnGen: Gen[DraftSingleDisposalReturn] =
    gen[DraftSingleDisposalReturn]

}

trait LowerPriorityDraftReturnGen { this: GenUtils =>

  implicit val multipleDisposalDraftReturnGen: Gen[DraftMultipleDisposalsReturn] = gen[DraftMultipleDisposalsReturn]

  implicit val multipleIndirectDisposalDraftReturnGen: Gen[DraftMultipleIndirectDisposalsReturn] =
    gen[DraftMultipleIndirectDisposalsReturn]

  implicit val singleIndirectDisposalDraftReturnGen: Gen[DraftSingleIndirectDisposalReturn] =
    gen[DraftSingleIndirectDisposalReturn]

  implicit val singleMixedUseDraftReturnGen: Gen[DraftSingleMixedUseDisposalReturn] =
    gen[DraftSingleMixedUseDisposalReturn]

}

trait UpscanGen { this: GenUtils =>
  implicit val uploadDetails: Gen[UploadDetails]        = gen[UploadDetails]
  implicit val newUpscanSuccess: Gen[NewUpscanSuccess]  = gen[NewUpscanSuccess]
  implicit val upscanUploadGen: Gen[UpscanUpload]       = gen[UpscanUpload]
  implicit val uploadReferenceGen: Gen[UploadReference] = gen[UploadReference]
  implicit val upscanSuccessGen: Gen[UpscanSuccess]     = gen[UpscanSuccess]
}

trait DmsSubmissionGen {
  this: GenUtils =>
  implicit val dmsMetadataGen: Gen[DmsMetadata]                   = gen[DmsMetadata]
  implicit val fileAttachmentGen: Gen[FileAttachment]             = gen[FileAttachment]
  implicit val dmsSubmissionPayloadGen: Gen[DmsSubmissionPayload] = gen[DmsSubmissionPayload]
  implicit val dmsSubmissionRequestGen: Gen[DmsSubmissionRequest] = gen[DmsSubmissionRequest]
  implicit val workItemGen: Gen[WorkItem[DmsSubmissionRequest]]   = gen[WorkItem[DmsSubmissionRequest]]
}

trait ReturnsGen extends LowerPriorityReturnsGen { this: GenUtils =>

  implicit val completeReturnGen: Gen[CompleteReturn] = gen[CompleteReturn]

  implicit val displayReturnGen: Gen[DisplayReturn] = gen[DisplayReturn]

  implicit val completeSingleDisposalReturnGen: Gen[CompleteSingleDisposalReturn] = gen[CompleteSingleDisposalReturn]

  implicit val completeSingleDisposalTriageAnswersGen: Gen[CompleteSingleDisposalTriageAnswers] =
    gen[CompleteSingleDisposalTriageAnswers]

  implicit val completeMultipleDisposalsTriageAnswersGen: Gen[CompleteMultipleDisposalsTriageAnswers] =
    gen[CompleteMultipleDisposalsTriageAnswers]

  implicit val completeExamplePropertyDetailsAnswersGen: Gen[CompleteExamplePropertyDetailsAnswers] =
    gen[CompleteExamplePropertyDetailsAnswers]

  implicit val completeDisposalDetailsAnswersGen: Gen[CompleteDisposalDetailsAnswers] =
    gen[CompleteDisposalDetailsAnswers]

  implicit val completeCalculatedYearToDateLiabilityAnswersGen: Gen[CompleteCalculatedYTDAnswers] =
    gen[CompleteCalculatedYTDAnswers]

  implicit val completeNonCalculatedYearToDateLiabilityAnswersGen: Gen[CompleteNonCalculatedYTDAnswers] =
    gen[CompleteNonCalculatedYTDAnswers]

  implicit val hasEstimatedDetailsWithCalculatedTaxDueGen: Gen[HasEstimatedDetailsWithCalculatedTaxDue] =
    gen[HasEstimatedDetailsWithCalculatedTaxDue]

  implicit val otherReliefsGen: Gen[OtherReliefs] = gen[OtherReliefs]

  implicit val calculatedTaxDueGen: Gen[CalculatedTaxDue] = gen[CalculatedTaxDue]

  implicit val gainCalculatedTaxDueGen: Gen[GainCalculatedTaxDue] = gen[GainCalculatedTaxDue]

  implicit val completeAcquisitionDetailsAnswersGen: Gen[CompleteAcquisitionDetailsAnswers] =
    gen[CompleteAcquisitionDetailsAnswers]

  implicit val completeExemptionAndLossesAnswersGen: Gen[CompleteExemptionAndLossesAnswers] =
    gen[CompleteExemptionAndLossesAnswers]

  implicit val listReturnResponseGen: Gen[ListReturnsResponse] =
    gen[ListReturnsResponse]

  implicit val submitReturnRequestGen: Gen[SubmitReturnRequest] = gen[SubmitReturnRequest]

  implicit val submitReturnResponseGen: Gen[SubmitReturnResponse] = gen[SubmitReturnResponse]

  implicit val taxYearGen: Gen[TaxYear] = gen[TaxYear]

  implicit val disposalDateGen: Gen[DisposalDate] = gen[DisposalDate]

  implicit val calculateCgtTaxDueRequestGen: Gen[CalculateCgtTaxDueRequest] =
    gen[CalculateCgtTaxDueRequest]

  implicit val returnSummaryGen: Gen[ReturnSummary] = gen[ReturnSummary]

  implicit val assetTypeGen: Gen[AssetType] = gen[AssetType]

  implicit val mandatoryEvidenceGen: Gen[MandatoryEvidence] = gen[MandatoryEvidence]

  implicit val supportingEvidenceGen: Gen[SupportingEvidence] = gen[SupportingEvidence]

  implicit val representeeDetailsGen: Gen[RepresenteeDetails] = gen[RepresenteeDetails]

  implicit val representeeContactDetailsGen: Gen[RepresenteeContactDetails] = gen[RepresenteeContactDetails]

  implicit val completeRepresenteeAnswersGen: Gen[CompleteRepresenteeAnswers] = gen[CompleteRepresenteeAnswers]

  implicit val completeExampleCompanyDetailsAnswersGen: Gen[CompleteExampleCompanyDetailsAnswers] =
    gen[CompleteExampleCompanyDetailsAnswers]

  implicit val completeSupportingEvidenceAnswersGen: Gen[CompleteSupportingEvidenceAnswers] =
    gen[CompleteSupportingEvidenceAnswers]

  implicit val amendReturnDataGen: Gen[AmendReturnData] = gen[AmendReturnData]

  implicit val completeReturnWithSummaryGen: Gen[CompleteReturnWithSummary] = gen[CompleteReturnWithSummary]

}

trait LowerPriorityReturnsGen { this: GenUtils =>

  implicit val nonGainCalculatedTaxDueGen: Gen[NonGainCalculatedTaxDue] = gen[NonGainCalculatedTaxDue]

  implicit val completeMultipleDisposalReturnGen: Gen[CompleteMultipleDisposalsReturn] =
    gen[CompleteMultipleDisposalsReturn]

  implicit val completeSingleIndirectDisposalReturnGen: Gen[CompleteSingleIndirectDisposalReturn] =
    gen[CompleteSingleIndirectDisposalReturn]

  implicit val completeMultipleIndirectDisposalReturnGen: Gen[CompleteMultipleIndirectDisposalReturn] =
    gen[CompleteMultipleIndirectDisposalReturn]

  implicit val completeSingleMixedUseDisposalReturnGen: Gen[CompleteSingleMixedUseDisposalReturn] =
    gen[CompleteSingleMixedUseDisposalReturn]

  implicit val completeMixedUsePropertyDetailsGen: Gen[CompleteMixedUsePropertyDetailsAnswers] =
    gen[CompleteMixedUsePropertyDetailsAnswers]

}

trait DesReturnsGen { this: GenUtils =>

  implicit val desReturnDetailsGen: Gen[DesReturnDetails] = gen[DesReturnDetails]

  implicit val desSingleDisposalDetailsGen: Gen[SingleDisposalDetails] = gen[SingleDisposalDetails]

  implicit val desMultipleDisposalsDetailsGen: Gen[MultipleDisposalDetails] =
    gen[MultipleDisposalDetails]

  implicit val desSingleMixedUseDisposalsDetailsGen: Gen[SingleMixedUseDisposalDetails] =
    gen[SingleMixedUseDisposalDetails]

  implicit val returnDetailsGen: Gen[ReturnDetails] = gen[ReturnDetails]

  implicit val desReliefDetailsGen: Gen[ReliefDetails] = gen[ReliefDetails]

  implicit val representedPersonDetailsGen: Gen[RepresentedPersonDetails] = gen[RepresentedPersonDetails]

  implicit val incomeAllowanceDetailsGen: Gen[IncomeAllowanceDetails] = gen[IncomeAllowanceDetails]

  implicit val desReturnSummaryGen: Gen[DesReturnSummary] = gen[DesReturnSummary]

  implicit val desFinancialTransactionGen: Gen[DesFinancialTransaction] = gen[DesFinancialTransaction]

  implicit val desSubmitReturnRequestGen: Gen[DesSubmitReturnRequest] = gen[DesSubmitReturnRequest]

  implicit val createReturnTypeGen: Gen[CreateReturnType] = gen[CreateReturnType]

  implicit val amendReturnTypeGen: Gen[AmendReturnType] = gen[AmendReturnType]

}

trait AddressGen extends AddressLowerPriorityGen { this: GenUtils =>

  implicit val addressGen: Gen[Address] = gen[Address]

  implicit val postcodeGen: Gen[Postcode] = Gen.oneOf(List(Postcode("BN11 3QY"), Postcode("BN11 4QY")))

  implicit val ukAddressGen: Gen[UkAddress] = {
    for {
      a <- gen[UkAddress]
      p <- postcodeGen
    } yield a.copy(postcode = p)
  }

  implicit val countryGen: Gen[Country] = Gen.oneOf(Country.countryCodes.map(Country(_)))

}

trait AddressLowerPriorityGen { this: GenUtils =>

  implicit val nonUkAddressGen: Gen[NonUkAddress] = gen[NonUkAddress]

}

trait MoneyGen { this: GenUtils =>

  implicit val amountInPenceGen: Gen[AmountInPence] = gen[AmountInPence]

  implicit val amountInPenceWithSourceGen: Gen[AmountInPenceWithSource] = gen[AmountInPenceWithSource]

}

trait FurtherReturnCalculationGen { this: GenUtils =>

  implicit val taxableGainOrLossCalculationRequestGen: Gen[TaxableGainOrLossCalculationRequest] =
    gen[TaxableGainOrLossCalculationRequest]

  implicit val taxableGainOrLossCalculationGen: Gen[TaxableGainOrLossCalculation] = gen[TaxableGainOrLossCalculation]

  implicit val ytdLiabilityCalculationRequestGen: Gen[YearToDateLiabilityCalculationRequest] =
    gen[YearToDateLiabilityCalculationRequest]

  implicit val ytdLiabilityCalculationGen: Gen[YearToDateLiabilityCalculation] = gen[YearToDateLiabilityCalculation]

}
