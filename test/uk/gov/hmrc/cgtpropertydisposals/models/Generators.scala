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

package uk.gov.hmrc.cgtpropertydisposals.models

import org.apache.pekko.util.ByteString
import org.bson.types.ObjectId
//import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.{Arbitrary, Gen}
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
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.{NewUpscanSuccess, UploadDetails, UpscanSuccess}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UploadReference, UpscanUpload, UpscanUploadWrapper}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionRequest
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.arb

import java.time.{Instant, LocalDate, LocalDateTime, ZoneId}
import java.util.Base64
import scala.reflect.{ClassTag, classTag}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DesReturnSummary
import io.github.martinhh.derived.scalacheck.given

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
  given stringArb: Arbitrary[String] = Arbitrary(
    for {
      n <- Gen.choose(1, 30)
      s <- Gen.listOfN(n, Gen.alphaChar).map(_.mkString(""))
    } yield s
  )

  given longArb: Arbitrary[Long] = Arbitrary(Gen.choose(0L, 100L))

  given bigDecimalGen: Arbitrary[BigDecimal] = Arbitrary(Gen.choose(0, 100).map(BigDecimal(_)))

  given localDateTimeArb: Arbitrary[LocalDateTime] =
    Arbitrary(
      Gen
        .chooseNum(0L, 10000L)
        .map(l => LocalDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault()))
    )

  /*  given localDateArb: Arbitrary[LocalDate] = Arbitrary(
    Gen.chooseNum(0, 10000L).map(LocalDate.ofEpochDay)
  )*/

  given byteStringArb: Arbitrary[ByteString] =
    Arbitrary(
      Gen
        .choose(0L, Long.MaxValue)
        .map(s => ByteString(s))
    )

  given bsonObjectId: Arbitrary[ObjectId] =
    Arbitrary(
      Gen
        .choose(0L, 10000L)
        .map(_ => ObjectId.get())
    )

  given instantArb: Arbitrary[Instant] =
    Arbitrary(
      Gen
        .chooseNum(0L, 10000L)
        .map(l => Instant.ofEpochMilli(l))
    )
}

trait IdGen {
  this: GenUtils =>

  given cgtReferenceGen: Gen[CgtReference] = gen[CgtReference]

  given sapNumberGen: Gen[SapNumber] = gen[SapNumber]

  given sautrGen: Gen[SAUTR] = gen[SAUTR]

  given ninoGen: Gen[NINO] = gen[NINO]

  given agentReferenceNumberGen: Gen[AgentReferenceNumber] = gen[AgentReferenceNumber]
}

trait B64HtmlGen {
  this: GenUtils =>
  given b64HtmlGen: Gen[B64Html] =
    Gen.asciiStr.map(s => B64Html(new String(Base64.getEncoder.encode(s.getBytes()))))
}

trait NameGen {
  this: GenUtils =>

  given individualNameGen: Gen[IndividualName] = gen[IndividualName]

  given trustNameGen: Gen[TrustName] = gen[TrustName]

  given contactNameGen: Gen[ContactName] = gen[ContactName]
}

trait EmailGen {
  this: GenUtils =>

  given emailGen: Gen[Email] = gen[Email]
}

trait OnboardingGen {
  this: GenUtils =>

  given registrationDetailsGen: Gen[RegistrationDetails] = gen[RegistrationDetails]

  given subscriptionDetailsGen: Gen[SubscriptionDetails] = gen[SubscriptionDetails]

  given subscriptionSuccessfulGen: Gen[SubscriptionSuccessful] = gen[SubscriptionSuccessful]

  given subscribedDetailsGen: Gen[SubscribedDetails] = gen[SubscribedDetails]

  given desSubscriptionRequestGen: Gen[DesSubscriptionRequest] = gen[DesSubscriptionRequest]

  given desUpdateRequestGen: Gen[DesSubscriptionUpdateRequest] = gen[DesSubscriptionUpdateRequest]
}

trait BusinessPartnerRecordGen {
  this: GenUtils =>

  given bprGen: Gen[BusinessPartnerRecord] = gen[BusinessPartnerRecord]

  given bprRequestGen: Gen[BusinessPartnerRecordRequest] = gen[BusinessPartnerRecordRequest]

  given individualBprRequestGen: Gen[IndividualBusinessPartnerRecordRequest] =
    gen[IndividualBusinessPartnerRecordRequest]
}

trait TaxEnrolmentGen {
  this: GenUtils =>

  given taxEnrolmentRequestGen: Gen[TaxEnrolmentRequest] = gen[TaxEnrolmentRequest]

  given updateVerifiersRequestGen: Gen[UpdateVerifiersRequest] = gen[UpdateVerifiersRequest]
}

trait DraftReturnGen extends LowerPriorityDraftReturnGen {
  this: GenUtils =>

  given draftReturnGen: Gen[DraftReturn] = gen[DraftReturn]

  given singleDisposalDraftReturnGen: Gen[DraftSingleDisposalReturn] =
    gen[DraftSingleDisposalReturn]
}

trait LowerPriorityDraftReturnGen {
  this: GenUtils =>

  given multipleDisposalDraftReturnGen: Gen[DraftMultipleDisposalsReturn] = gen[DraftMultipleDisposalsReturn]

  given multipleIndirectDisposalDraftReturnGen: Gen[DraftMultipleIndirectDisposalsReturn] =
    gen[DraftMultipleIndirectDisposalsReturn]

  given singleIndirectDisposalDraftReturnGen: Gen[DraftSingleIndirectDisposalReturn] =
    gen[DraftSingleIndirectDisposalReturn]

  given singleMixedUseDraftReturnGen: Gen[DraftSingleMixedUseDisposalReturn] =
    gen[DraftSingleMixedUseDisposalReturn]
}

trait UpscanGen {
  this: GenUtils =>
  given uploadDetails: Gen[UploadDetails]            = gen[UploadDetails]
  given newUpscanSuccess: Gen[NewUpscanSuccess]      = gen[NewUpscanSuccess]
  given upscanUploadGen: Gen[UpscanUpload]           = gen[UpscanUpload]
  given upscanUploadNewGen: Gen[UpscanUploadWrapper] = gen[UpscanUploadWrapper]
  given uploadReferenceGen: Gen[UploadReference]     = gen[UploadReference]
  given upscanSuccessGen: Gen[UpscanSuccess]         = gen[UpscanSuccess]
}

trait DmsSubmissionGen {
  this: GenUtils =>
  given dmsMetadataGen: Gen[DmsMetadata]                   = gen[DmsMetadata]
  given fileAttachmentGen: Gen[FileAttachment]             = gen[FileAttachment]
  given dmsSubmissionPayloadGen: Gen[DmsSubmissionPayload] = gen[DmsSubmissionPayload]
  given dmsSubmissionRequestGen: Gen[DmsSubmissionRequest] = gen[DmsSubmissionRequest]
  given workItemGen: Gen[WorkItem[DmsSubmissionRequest]]   = gen[WorkItem[DmsSubmissionRequest]]
}

trait ReturnsGen extends LowerPriorityReturnsGen {
  this: GenUtils =>

  given completeReturnGen: Gen[CompleteReturn] = gen[CompleteReturn]

  given displayReturnGen: Gen[DisplayReturn] = gen[DisplayReturn]

  given completeSingleDisposalReturnGen: Gen[CompleteSingleDisposalReturn] = gen[CompleteSingleDisposalReturn]

  given completeSingleDisposalTriageAnswersGen: Gen[CompleteSingleDisposalTriageAnswers] =
    gen[CompleteSingleDisposalTriageAnswers]

  given completeMultipleDisposalsTriageAnswersGen: Gen[CompleteMultipleDisposalsTriageAnswers] =
    gen[CompleteMultipleDisposalsTriageAnswers]

  given completeExamplePropertyDetailsAnswersGen: Gen[CompleteExamplePropertyDetailsAnswers] =
    gen[CompleteExamplePropertyDetailsAnswers]

  given completeDisposalDetailsAnswersGen: Gen[CompleteDisposalDetailsAnswers] =
    gen[CompleteDisposalDetailsAnswers]

  given completeCalculatedYearToDateLiabilityAnswersGen: Gen[CompleteCalculatedYTDAnswers] =
    gen[CompleteCalculatedYTDAnswers]

  given completeNonCalculatedYearToDateLiabilityAnswersGen: Gen[CompleteNonCalculatedYTDAnswers] =
    gen[CompleteNonCalculatedYTDAnswers]

  given hasEstimatedDetailsWithCalculatedTaxDueGen: Gen[HasEstimatedDetailsWithCalculatedTaxDue] =
    gen[HasEstimatedDetailsWithCalculatedTaxDue]

  given otherReliefsGen: Gen[OtherReliefs] = gen[OtherReliefs]

  given calculatedTaxDueGen: Gen[CalculatedTaxDue] = gen[CalculatedTaxDue]

  given gainCalculatedTaxDueGen: Gen[GainCalculatedTaxDue] = gen[GainCalculatedTaxDue]

  given completeAcquisitionDetailsAnswersGen: Gen[CompleteAcquisitionDetailsAnswers] =
    gen[CompleteAcquisitionDetailsAnswers]

  given completeExemptionAndLossesAnswersGen: Gen[CompleteExemptionAndLossesAnswers] =
    gen[CompleteExemptionAndLossesAnswers]

  given listReturnResponseGen: Gen[ListReturnsResponse] =
    gen[ListReturnsResponse]

  given submitReturnRequestGen: Gen[SubmitReturnRequest] = gen[SubmitReturnRequest]

  given submitReturnWrapperGen: Gen[SubmitReturnWrapper] = gen[SubmitReturnWrapper]

  given submitReturnResponseGen: Gen[SubmitReturnResponse] = gen[SubmitReturnResponse]

  given taxYearGen: Gen[TaxYear] = gen[TaxYear]

  given taxYearConfigGen: Gen[TaxYearConfig] = gen[TaxYearConfig]

  given disposalDateGen: Gen[DisposalDate] = gen[DisposalDate]

  given calculateCgtTaxDueRequestGen: Gen[CalculateCgtTaxDueRequest] =
    gen[CalculateCgtTaxDueRequest]

  given returnSummaryGen: Gen[ReturnSummary] = gen[ReturnSummary]

  given assetTypeGen: Gen[AssetType] = gen[AssetType]

  given mandatoryEvidenceGen: Gen[MandatoryEvidence] = gen[MandatoryEvidence]

  given supportingEvidenceGen: Gen[SupportingEvidence] = gen[SupportingEvidence]

  given representeeDetailsGen: Gen[RepresenteeDetails] = gen[RepresenteeDetails]

  given representeeContactDetailsGen: Gen[RepresenteeContactDetails] = gen[RepresenteeContactDetails]

  given completeRepresenteeAnswersGen: Gen[CompleteRepresenteeAnswers] = gen[CompleteRepresenteeAnswers]

  given completeExampleCompanyDetailsAnswersGen: Gen[CompleteExampleCompanyDetailsAnswers] =
    gen[CompleteExampleCompanyDetailsAnswers]

  given completeSupportingEvidenceAnswersGen: Gen[CompleteSupportingEvidenceAnswers] =
    gen[CompleteSupportingEvidenceAnswers]

  given amendReturnDataGen: Gen[AmendReturnData] = gen[AmendReturnData]

  given completeReturnWithSummaryGen: Gen[CompleteReturnWithSummary] = gen[CompleteReturnWithSummary]
}

trait LowerPriorityReturnsGen {
  this: GenUtils =>

  given nonGainCalculatedTaxDueGen: Gen[NonGainCalculatedTaxDue] = gen[NonGainCalculatedTaxDue]

  given completeMultipleDisposalReturnGen: Gen[CompleteMultipleDisposalsReturn] =
    gen[CompleteMultipleDisposalsReturn]

  given completeSingleIndirectDisposalReturnGen: Gen[CompleteSingleIndirectDisposalReturn] =
    gen[CompleteSingleIndirectDisposalReturn]

  given completeMultipleIndirectDisposalReturnGen: Gen[CompleteMultipleIndirectDisposalReturn] =
    gen[CompleteMultipleIndirectDisposalReturn]

  given completeSingleMixedUseDisposalReturnGen: Gen[CompleteSingleMixedUseDisposalReturn] =
    gen[CompleteSingleMixedUseDisposalReturn]

  given completeMixedUsePropertyDetailsGen: Gen[CompleteMixedUsePropertyDetailsAnswers] =
    gen[CompleteMixedUsePropertyDetailsAnswers]
}

trait DesReturnsGen {
  this: GenUtils =>

  given desReturnDetailsGen: Gen[DesReturnDetails] = gen[DesReturnDetails]

  given desSingleDisposalDetailsGen: Gen[SingleDisposalDetails] = gen[SingleDisposalDetails]

  given desMultipleDisposalsDetailsGen: Gen[MultipleDisposalDetails] =
    gen[MultipleDisposalDetails]

  given desSingleMixedUseDisposalsDetailsGen: Gen[SingleMixedUseDisposalDetails] =
    gen[SingleMixedUseDisposalDetails]

  given returnDetailsGen: Gen[ReturnDetails] = gen[ReturnDetails]

  given desReliefDetailsGen: Gen[ReliefDetails] = gen[ReliefDetails]

  given representedPersonDetailsGen: Gen[RepresentedPersonDetails] = gen[RepresentedPersonDetails]

  given incomeAllowanceDetailsGen: Gen[IncomeAllowanceDetails] = gen[IncomeAllowanceDetails]

  given desReturnSummaryGen: Gen[DesReturnSummary] = gen[DesReturnSummary]

  given desFinancialTransactionGen: Gen[DesFinancialTransaction] = gen[DesFinancialTransaction]

  given desSubmitReturnRequestGen: Gen[DesSubmitReturnRequest] = gen[DesSubmitReturnRequest]

  given createReturnTypeGen: Gen[CreateReturnType] = gen[CreateReturnType]

  given amendReturnTypeGen: Gen[AmendReturnType] = gen[AmendReturnType]
}

trait AddressGen extends AddressLowerPriorityGen {
  this: GenUtils =>

  given addressGen: Gen[Address] = gen[Address]

  given postcodeGen: Gen[Postcode] = Gen.oneOf(List(Postcode("BN11 3QY"), Postcode("BN11 4QY")))

  given ukAddressGen: Gen[UkAddress] =
    for {
      a <- gen[UkAddress]
      p <- postcodeGen
    } yield a.copy(postcode = p)

  given countryGen: Gen[Country] = Gen.oneOf(Country.countryCodes.map(Country(_)))
}

trait AddressLowerPriorityGen {
  this: GenUtils =>

  given nonUkAddressGen: Gen[NonUkAddress] = gen[NonUkAddress]
}

trait MoneyGen {
  this: GenUtils =>

  given amountInPenceGen: Gen[AmountInPence] = gen[AmountInPence]

  given amountInPenceWithSourceGen: Gen[AmountInPenceWithSource] = gen[AmountInPenceWithSource]
}

trait FurtherReturnCalculationGen {
  this: GenUtils =>

  given taxableGainOrLossCalculationRequestGen: Gen[TaxableGainOrLossCalculationRequest] =
    gen[TaxableGainOrLossCalculationRequest]

  given taxableGainOrLossCalculationGen: Gen[TaxableGainOrLossCalculation] = gen[TaxableGainOrLossCalculation]

  given ytdLiabilityCalculationRequestGen: Gen[YearToDateLiabilityCalculationRequest] =
    gen[YearToDateLiabilityCalculationRequest]

  given ytdLiabilityCalculationGen: Gen[YearToDateLiabilityCalculation] = gen[YearToDateLiabilityCalculation]
}
