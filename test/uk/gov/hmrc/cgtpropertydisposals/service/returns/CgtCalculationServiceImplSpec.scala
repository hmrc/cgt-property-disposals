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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import io.github.martinhh.derived.scalacheck.given
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.generators.AddressGen.ukAddressGen
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.MoneyGen.amountInPenceGen
import uk.gov.hmrc.cgtpropertydisposals.models.generators.ReturnsGen.{completeAcquisitionDetailsAnswersGen, completeDisposalDetailsAnswersGen, completeExemptionAndLossesAnswersGen, completeSingleDisposalTriageAnswersGen, disposalDateGen}
import uk.gov.hmrc.cgtpropertydisposals.models.generators.TaxYearGen.taxYearGen
import uk.gov.hmrc.cgtpropertydisposals.models.returns.*
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers

class CgtCalculationServiceImplSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  val service = new CgtCalculationServiceImpl

  "CgtCalculationServiceImpl" when {

    "calculating tax due" must {
      val completeTriageAnswers = sample[CompleteSingleDisposalTriageAnswers]

      val zeroDisposalDetails    = sample[CompleteDisposalDetailsAnswers].copy(
        disposalPrice = AmountInPence.zero,
        disposalFees = AmountInPence.zero
      )
      val zeroAcquisitionDetails = sample[CompleteAcquisitionDetailsAnswers].copy(
        acquisitionPrice = AmountInPence.zero,
        improvementCosts = AmountInPence.zero,
        acquisitionFees = AmountInPence.zero,
        rebasedAcquisitionPrice = None,
        shouldUseRebase = false
      )

      val zeroReliefDetails = CompleteReliefDetailsAnswers(
        AmountInPence.zero,
        AmountInPence.zero,
        None
      )

      val zeroExemptionsAndLosses = CompleteExemptionAndLossesAnswers(
        AmountInPence.zero,
        AmountInPence.zero,
        AmountInPence.zero
      )

      def calculate(
        triageAnswers: CompleteSingleDisposalTriageAnswers = completeTriageAnswers,
        disposalDetails: CompleteDisposalDetailsAnswers = zeroDisposalDetails,
        acquisitionDetails: CompleteAcquisitionDetailsAnswers = zeroAcquisitionDetails,
        reliefDetails: CompleteReliefDetailsAnswers = zeroReliefDetails,
        exemptionAndLosses: CompleteExemptionAndLossesAnswers = zeroExemptionsAndLosses,
        estimatedIncome: AmountInPence = sample[AmountInPence],
        personalAllowance: AmountInPence = sample[AmountInPence],
        initialGainOrLoss: Option[AmountInPence] = None,
        isATrust: Boolean = false
      ) =
        service.calculateTaxDue(
          triageAnswers,
          sample[UkAddress],
          disposalDetails,
          acquisitionDetails,
          reliefDetails,
          exemptionAndLosses,
          estimatedIncome,
          personalAllowance,
          initialGainOrLoss,
          isATrust
        )

      "calculate disposal amount less costs correctly" in {
        forAll { (disposalPrice: AmountInPence, disposalFees: AmountInPence) =>
          val result =
            calculate(
              disposalDetails =
                sample[CompleteDisposalDetailsAnswers].copy(disposalPrice = disposalPrice, disposalFees = disposalFees)
            )

          result.disposalAmountLessCosts.value shouldBe (disposalPrice.value - disposalFees.value)
        }
      }

      "calculate acquisition amount plus costs correctly" when {

        "the user has not rebased" in {
          forAll { (acquisitionPrice: AmountInPence, improvementCosts: AmountInPence, acquisitionFees: AmountInPence) =>
            val result =
              calculate(
                acquisitionDetails = sample[CompleteAcquisitionDetailsAnswers].copy(
                  acquisitionPrice = acquisitionPrice,
                  improvementCosts = improvementCosts,
                  acquisitionFees = acquisitionFees,
                  rebasedAcquisitionPrice = None,
                  shouldUseRebase = false
                )
              )

            result.acquisitionAmountPlusCosts.value shouldBe (acquisitionPrice.value + improvementCosts.value + acquisitionFees.value)
          }
        }

        "the user has rebased and chosen to use the rebased value" in {
          forAll {
            (
              acquisitionPrice: AmountInPence,
              rebasedAcquisitionPrice: AmountInPence,
              improvementCosts: AmountInPence,
              acquisitionFees: AmountInPence
            ) =>
              val result =
                calculate(
                  acquisitionDetails = zeroAcquisitionDetails.copy(
                    acquisitionPrice = acquisitionPrice,
                    improvementCosts = improvementCosts,
                    acquisitionFees = acquisitionFees,
                    rebasedAcquisitionPrice = Some(rebasedAcquisitionPrice),
                    shouldUseRebase = true
                  )
                )

              result.acquisitionAmountPlusCosts.value shouldBe (rebasedAcquisitionPrice.value + improvementCosts.value + acquisitionFees.value)
          }
        }

        "the user has rebased and chosen not to use the rebased value" in {
          forAll {
            (
              acquisitionPrice: AmountInPence,
              rebasedAcquisitionPrice: AmountInPence,
              improvementCosts: AmountInPence,
              acquisitionFees: AmountInPence
            ) =>
              val result =
                calculate(
                  acquisitionDetails = zeroAcquisitionDetails.copy(
                    acquisitionPrice = acquisitionPrice,
                    improvementCosts = improvementCosts,
                    acquisitionFees = acquisitionFees,
                    rebasedAcquisitionPrice = Some(rebasedAcquisitionPrice),
                    shouldUseRebase = false
                  )
                )

              result.acquisitionAmountPlusCosts.value shouldBe (acquisitionPrice.value + improvementCosts.value + acquisitionFees.value)
          }
        }

      }

      "calculate initial gain or loss correctly" in {
        forAll {
          (disposalDetails: CompleteDisposalDetailsAnswers, acquisitionDetails: CompleteAcquisitionDetailsAnswers) =>
            val result = calculate(disposalDetails = disposalDetails, acquisitionDetails = acquisitionDetails)

            result.initialGainOrLoss.amount.value shouldBe (result.disposalAmountLessCosts.value - result.acquisitionAmountPlusCosts.value)
        }
      }

      "not calculate initial gain or loss when user supplied" in {
        forAll {
          (disposalDetails: CompleteDisposalDetailsAnswers, acquisitionDetails: CompleteAcquisitionDetailsAnswers) =>
            val expectedInitialGainOrLoss = AmountInPence(10L)
            val result                    = calculate(
              disposalDetails = disposalDetails,
              acquisitionDetails = acquisitionDetails,
              initialGainOrLoss = Some(expectedInitialGainOrLoss)
            )

            result.initialGainOrLoss.amount.value shouldBe expectedInitialGainOrLoss.value
            result.initialGainOrLoss.source       shouldBe Source.UserSupplied
        }
      }

      "calculate total reliefs correctly" when {

        "the user has not had the option to enter other reliefs" in {
          forAll { (privateResidentsRelief: AmountInPence, lettingRelief: AmountInPence) =>
            val result = calculate(
              reliefDetails = CompleteReliefDetailsAnswers(privateResidentsRelief, lettingRelief, None)
            )
            result.totalReliefs.value shouldBe (privateResidentsRelief.value + lettingRelief.value)
          }
        }

        "the user has had the option to enter other reliefs and has chosen not to" in {
          forAll { (privateResidentsRelief: AmountInPence, lettingRelief: AmountInPence) =>
            val result = calculate(
              reliefDetails = CompleteReliefDetailsAnswers(
                privateResidentsRelief,
                lettingRelief,
                Some(OtherReliefsOption.NoOtherReliefs)
              )
            )
            result.totalReliefs.value shouldBe (privateResidentsRelief.value + lettingRelief.value)
          }
        }

        "the user has had the option to enter other reliefs and has chosen to" in {
          forAll {
            (
              privateResidentsRelief: AmountInPence,
              lettingRelief: AmountInPence
            ) =>
              val result = calculate(
                reliefDetails = CompleteReliefDetailsAnswers(privateResidentsRelief, lettingRelief, None)
              )
              result.totalReliefs.value shouldBe (privateResidentsRelief.value + lettingRelief.value)
          }
        }

      }

      "calculate gain or loss after reliefs correctly" when {

        "there is an initial gain and" when {

          val disposalDetails = zeroDisposalDetails.copy(
            disposalPrice = AmountInPence(100L)
          )

          "the initial gain is greater than the total reliefs" in {
            val reliefDetails = CompleteReliefDetailsAnswers(
              AmountInPence(1L),
              AmountInPence(1L),
              None
            )
            val result        = calculate(
              disposalDetails = disposalDetails,
              acquisitionDetails = zeroAcquisitionDetails,
              reliefDetails = reliefDetails
            )

            result.initialGainOrLoss.amount.value shouldBe 100L
            result.initialGainOrLoss.source       shouldBe Source.Calculated
            result.gainOrLossAfterReliefs.value   shouldBe 98L

          }

          "the initial gain is less than the total reliefs" in {
            val reliefDetails = CompleteReliefDetailsAnswers(
              AmountInPence(100L),
              AmountInPence(100L),
              None
            )
            val result        = calculate(
              disposalDetails = disposalDetails,
              acquisitionDetails = zeroAcquisitionDetails,
              reliefDetails = reliefDetails
            )

            result.initialGainOrLoss.amount.value shouldBe 100L
            result.initialGainOrLoss.source       shouldBe Source.Calculated
            result.gainOrLossAfterReliefs.value   shouldBe 0L
          }
        }
        "there is an initial loss and" when {

          val acquisitionDetails = zeroAcquisitionDetails.copy(
            acquisitionPrice = AmountInPence(100L)
          )

          "the absolute value of the initial loss is greater than the total reliefs" in {
            val reliefDetails = CompleteReliefDetailsAnswers(
              AmountInPence(1L),
              AmountInPence(1L),
              None
            )
            val result        = calculate(
              disposalDetails = zeroDisposalDetails,
              acquisitionDetails = acquisitionDetails,
              reliefDetails = reliefDetails
            )

            result.initialGainOrLoss.amount.value shouldBe -100L
            result.initialGainOrLoss.source       shouldBe Source.Calculated
            result.gainOrLossAfterReliefs.value   shouldBe -98L
          }

          "the absolute value of the initial loss is less than the total reliefs" in {
            val reliefDetails = CompleteReliefDetailsAnswers(
              AmountInPence(100L),
              AmountInPence(100L),
              None
            )
            val result        = calculate(
              disposalDetails = zeroDisposalDetails,
              acquisitionDetails = acquisitionDetails,
              reliefDetails = reliefDetails
            )

            result.initialGainOrLoss.amount.value shouldBe -100L
            result.initialGainOrLoss.source       shouldBe Source.Calculated
            result.gainOrLossAfterReliefs.value   shouldBe 0L
          }
        }
        "there is neither an initial gain or less" in {
          val reliefDetails = CompleteReliefDetailsAnswers(
            AmountInPence(1L),
            AmountInPence(1L),
            None
          )
          val result        = calculate(
            disposalDetails = zeroDisposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = reliefDetails
          )

          result.initialGainOrLoss.amount.value shouldBe 0L
          result.initialGainOrLoss.source       shouldBe Source.Calculated
          result.gainOrLossAfterReliefs.value   shouldBe 0L
        }

      }

      "calculate year position correctly" when {

        "the gain or loss after reliefs is negative" in {
          val acquisitionDetails = zeroAcquisitionDetails.copy(
            acquisitionPrice = AmountInPence(100L)
          )

          val result = calculate(
            disposalDetails = zeroDisposalDetails,
            acquisitionDetails = acquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(1L)
            )
          )

          result.gainOrLossAfterReliefs.value shouldBe -100L
          result.yearPosition.value           shouldBe -101L
        }

        "the gain or loss after reliefs is positive" in {
          val disposalDetails = zeroDisposalDetails.copy(
            disposalPrice = AmountInPence(100L)
          )

          val result = calculate(
            disposalDetails = disposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(99L),
              annualExemptAmount = AmountInPence(1L)
            )
          )

          result.gainOrLossAfterReliefs.value shouldBe 100L
          result.yearPosition.value           shouldBe 0L
        }

        "the gain or loss after reliefs is zero" in {
          val result = calculate(
            disposalDetails = zeroDisposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(1L),
              annualExemptAmount = AmountInPence(1000L)
            )
          )

          result.gainOrLossAfterReliefs.value shouldBe 0L
          // annual exempt amount can't be applied since gain or loss after reliefs
          // is less than in year losses
          result.yearPosition.value           shouldBe -1L
        }

        "the gain or loss after reliefs is strictly less than the in year losses" in {
          val disposalDetails = zeroDisposalDetails.copy(
            disposalPrice = AmountInPence(100L)
          )

          val result = calculate(
            disposalDetails = disposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(200L),
              annualExemptAmount = AmountInPence(100L)
            )
          )

          result.gainOrLossAfterReliefs.value shouldBe 100L
          result.yearPosition.value           shouldBe -100L
        }

        "the gain or loss after reliefs is strictly greater than the in year losses" in {
          val disposalDetails = zeroDisposalDetails.copy(
            disposalPrice = AmountInPence(100L)
          )

          val result = calculate(
            disposalDetails = disposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(1L),
              annualExemptAmount = AmountInPence(3L)
            )
          )

          result.gainOrLossAfterReliefs.value shouldBe 100L
          result.yearPosition.value           shouldBe 96L
        }

        "the gain or loss after reliefs is equal to the in year losses" in {
          val disposalDetails = zeroDisposalDetails.copy(
            disposalPrice = AmountInPence(100L)
          )

          val result = calculate(
            disposalDetails = disposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(100L),
              annualExemptAmount = AmountInPence(3L)
            )
          )

          result.gainOrLossAfterReliefs.value shouldBe 100L
          result.yearPosition.value           shouldBe 0L
        }

      }

      "calculate gain or loss after losses correctly" when {

        "the gain or loss after reliefs is negative and its absolute value " +
          "is greater than the in year losses" in {
            val acquisitionDetails = zeroAcquisitionDetails.copy(
              acquisitionPrice = AmountInPence(100L)
            )

            val result = calculate(
              disposalDetails = zeroDisposalDetails,
              acquisitionDetails = acquisitionDetails,
              reliefDetails = zeroReliefDetails,
              exemptionAndLosses = zeroExemptionsAndLosses.copy(
                inYearLosses = AmountInPence(1L)
              )
            )

            result.gainOrLossAfterReliefs.value shouldBe -100L
            result.yearPosition.value           shouldBe -101L
          }

        "the gain or loss after reliefs is negative and its absolute value " +
          "is less than the in year losses" in {
            val acquisitionDetails = zeroAcquisitionDetails.copy(
              acquisitionPrice = AmountInPence(100L)
            )

            val result = calculate(
              disposalDetails = zeroDisposalDetails,
              acquisitionDetails = acquisitionDetails,
              reliefDetails = zeroReliefDetails,
              exemptionAndLosses = zeroExemptionsAndLosses.copy(
                inYearLosses = AmountInPence(200L),
                annualExemptAmount = AmountInPence(2L)
              )
            )

            result.gainOrLossAfterReliefs.value shouldBe -100L
            result.yearPosition.value           shouldBe -300L
          }

        "the gain or loss after reliefs is zero" in {
          val result = calculate(
            disposalDetails = zeroDisposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(1L),
              annualExemptAmount = AmountInPence(2L)
            )
          )

          result.gainOrLossAfterReliefs.value shouldBe 0L
          result.yearPosition.value           shouldBe -1L
        }

      }

      "calculate taxable gain or net loss correctly" when {

        "the gain or loss after losses is negative" in {
          val acquisitionDetails = zeroAcquisitionDetails.copy(
            acquisitionPrice = AmountInPence(100L)
          )

          val result = calculate(
            disposalDetails = zeroDisposalDetails,
            acquisitionDetails = acquisitionDetails,
            reliefDetails = zeroReliefDetails.copy(privateResidentsRelief = AmountInPence(2L)),
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              inYearLosses = AmountInPence(1L)
            )
          )

          result                                shouldBe a[NonGainCalculatedTaxDue]
          result.initialGainOrLoss.amount.value shouldBe -100L
          result.gainOrLossAfterReliefs.value   shouldBe -98L
          result.taxableGainOrNetLoss.value     shouldBe -99L
        }

        "the gain or loss after losses is zero" in {
          val result = calculate(
            disposalDetails = zeroDisposalDetails,
            acquisitionDetails = zeroAcquisitionDetails,
            reliefDetails = zeroReliefDetails,
            exemptionAndLosses = zeroExemptionsAndLosses.copy(
              annualExemptAmount = AmountInPence(1L)
            )
          )

          result                            shouldBe a[NonGainCalculatedTaxDue]
          result.taxableGainOrNetLoss.value shouldBe 0L
        }

        "the gain or loss after losses is positive and is greater than " +
          "the annual exempt amount used" in {
            val disposalDetails = zeroDisposalDetails.copy(
              disposalPrice = AmountInPence(100L)
            )

            val result = calculate(
              disposalDetails = disposalDetails,
              acquisitionDetails = zeroAcquisitionDetails,
              reliefDetails = zeroReliefDetails,
              exemptionAndLosses = zeroExemptionsAndLosses.copy(
                annualExemptAmount = AmountInPence(1L)
              )
            )

            result                            shouldBe a[GainCalculatedTaxDue]
            result.yearPosition.value         shouldBe 99L
            result.taxableGainOrNetLoss.value shouldBe 99L
          }

        "the gain or loss after losses is positive and is less than " +
          "the annual exempt amount used" in {
            val disposalDetails = zeroDisposalDetails.copy(
              disposalPrice = AmountInPence(100L)
            )

            val result = calculate(
              disposalDetails = disposalDetails,
              acquisitionDetails = zeroAcquisitionDetails,
              reliefDetails = zeroReliefDetails,
              exemptionAndLosses = zeroExemptionsAndLosses.copy(
                annualExemptAmount = AmountInPence(200L)
              )
            )

            result                            shouldBe a[NonGainCalculatedTaxDue]
            result.yearPosition.value         shouldBe 0L
            result.taxableGainOrNetLoss.value shouldBe 0L
          }

      }

      "calculate taxable income correctly" when {

        "the estimated income is greater than or equal to the persona allowance used" in {
          forAll { (estimatedIncome: AmountInPence, personalAllowance: AmountInPence) =>
            whenever(estimatedIncome.value - personalAllowance.value >= 0L) {
              val disposalDetails = zeroDisposalDetails.copy(disposalPrice = AmountInPence(100L))
              val result          = calculate(
                disposalDetails = disposalDetails,
                estimatedIncome = estimatedIncome,
                personalAllowance = personalAllowance
              )

              testOnGainCalculatedTaxDue(result) {
                _.taxableIncome.value shouldBe (estimatedIncome.value - personalAllowance.value)
              }
            }
          }
        }

        "the estimated income is less to the persona allowance used" in {
          forAll { (estimatedIncome: AmountInPence, personalAllowance: AmountInPence) =>
            whenever(estimatedIncome.value - personalAllowance.value < 0L) {
              val disposalDetails = zeroDisposalDetails.copy(disposalPrice = AmountInPence(100L))
              val result          = calculate(
                disposalDetails = disposalDetails,
                estimatedIncome = estimatedIncome,
                personalAllowance = personalAllowance
              )

              testOnGainCalculatedTaxDue(result) {
                _.taxableIncome.value shouldBe 0L
              }
            }
          }
        }

      }

      "calculate the amount to be taxed at the lower band rate" when {

        def test(assetType: AssetType, expectedLowerBandRate: TaxYear => BigDecimal): Unit = {
          val incomeTaxHigherBandThreshold = AmountInPence(1000L)
          val taxYear                      = sample[TaxYear].copy(incomeTaxHigherRateThreshold = incomeTaxHigherBandThreshold)

          val triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(
            disposalDate = sample[DisposalDate].copy(taxYear = taxYear),
            assetType = assetType
          )

          "the taxable gain is less than the income tax higher rate threshold minus " +
            "the taxable income" in {
              val disposalDetails =
                zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))

              val result = calculate(
                triageAnswers = triageAnswers,
                disposalDetails = disposalDetails,
                estimatedIncome = AmountInPence(100L),
                personalAllowance = AmountInPence.zero
              )

              testOnGainCalculatedTaxDue(result) { calculated =>
                calculated.taxableGainOrNetLoss.value shouldBe 500L
                calculated.taxableIncome.value        shouldBe 100L
                calculated.taxDueAtLowerRate          shouldBe TaxableAmountOfMoney(
                  expectedLowerBandRate(taxYear),
                  AmountInPence(500L)
                )

              }

            }

          "the taxable gain is greater than the income tax higher rate threshold minus " +
            "the taxable income" in {
              val disposalDetails =
                zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))

              val result = calculate(
                triageAnswers = triageAnswers,
                disposalDetails = disposalDetails,
                estimatedIncome = AmountInPence(600L),
                personalAllowance = AmountInPence.zero
              )

              testOnGainCalculatedTaxDue(result) { calculated =>
                calculated.taxableGainOrNetLoss.value shouldBe 500L
                calculated.taxableIncome.value        shouldBe 600L
                calculated.taxDueAtLowerRate          shouldBe TaxableAmountOfMoney(
                  expectedLowerBandRate(taxYear),
                  AmountInPence(400L)
                )

              }
            }

          "the taxable gain is equal to the income tax higher rate threshold minus " +
            "the taxable income" in {
              val disposalDetails =
                zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))

              val result = calculate(
                triageAnswers = triageAnswers,
                disposalDetails = disposalDetails,
                estimatedIncome = AmountInPence(500L),
                personalAllowance = AmountInPence.zero
              )

              testOnGainCalculatedTaxDue(result) { calculated =>
                calculated.taxableGainOrNetLoss.value shouldBe 500L
                calculated.taxableIncome.value        shouldBe 500L
                calculated.taxDueAtLowerRate          shouldBe TaxableAmountOfMoney(
                  expectedLowerBandRate(taxYear),
                  AmountInPence(500L)
                )
              }
            }

          "the income tax higher rate threshold minus the taxable income is negative" in {
            val disposalDetails =
              zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))

            val result = calculate(
              triageAnswers = triageAnswers,
              disposalDetails = disposalDetails,
              estimatedIncome = AmountInPence(2000L),
              personalAllowance = AmountInPence.zero
            )

            testOnGainCalculatedTaxDue(result) { calculated =>
              calculated.taxableGainOrNetLoss.value shouldBe 500L
              calculated.taxableIncome.value        shouldBe 2000L
              calculated.taxDueAtLowerRate          shouldBe TaxableAmountOfMoney(
                expectedLowerBandRate(taxYear),
                AmountInPence(0L)
              )
            }
          }

          "the income tax higher rate threshold minus the taxable income is zero" in {
            val disposalDetails =
              zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))

            val result = calculate(
              triageAnswers = triageAnswers,
              disposalDetails = disposalDetails,
              estimatedIncome = AmountInPence(1000L),
              personalAllowance = AmountInPence.zero
            )

            testOnGainCalculatedTaxDue(result) { calculated =>
              calculated.taxableGainOrNetLoss.value shouldBe 500L
              calculated.taxableIncome.value        shouldBe 1000L
              calculated.taxDueAtLowerRate          shouldBe TaxableAmountOfMoney(
                expectedLowerBandRate(taxYear),
                AmountInPence(0L)
              )
            }
          }

        }

        "the asset type is residential and" when {

          behave like test(
            AssetType.Residential,
            _.cgtRateLowerBandResidential
          )

        }

        "the asset type is non-residential and" when {

          behave like test(
            AssetType.NonResidential,
            _.cgtRateLowerBandNonResidential
          )

        }

        "the user is a trust" in {
          val incomeTaxHigherBandThreshold = AmountInPence(1000L)
          val taxYear                      = sample[TaxYear].copy(incomeTaxHigherRateThreshold = incomeTaxHigherBandThreshold)

          val triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(
            disposalDate = sample[DisposalDate].copy(taxYear = taxYear),
            assetType = AssetType.Residential
          )

          val disposalDetails =
            zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))

          val result = calculate(
            triageAnswers = triageAnswers,
            disposalDetails = disposalDetails,
            estimatedIncome = AmountInPence(100L),
            personalAllowance = AmountInPence.zero,
            isATrust = true
          )

          testOnGainCalculatedTaxDue(result) { calculated =>
            calculated.taxableGainOrNetLoss.value shouldBe 500L
            calculated.taxableIncome.value        shouldBe 100L
            calculated.taxDueAtLowerRate          shouldBe TaxableAmountOfMoney(
              taxYear.cgtRateLowerBandResidential,
              AmountInPence(0L)
            )
          }

        }

      }

      "calculate the amount to be taxed at the higher band rate" when {

        def test(assetType: AssetType, expectedHigherRate: TaxYear => BigDecimal): Unit = {
          val incomeTaxHigherRateThreshold = AmountInPence(1000L)
          val taxYear                      = sample[TaxYear].copy(incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold)
          val triageAnswers                = sample[CompleteSingleDisposalTriageAnswers].copy(
            disposalDate = sample[DisposalDate].copy(taxYear = taxYear),
            assetType = assetType
          )

          "the taxable gain is greater than the amount to be taxed at the lower rate" in {
            val disposalDetails = zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))
            val taxableIncome   = AmountInPence(600L)

            testOnGainCalculatedTaxDue(
              calculate(
                triageAnswers = triageAnswers,
                disposalDetails = disposalDetails,
                estimatedIncome = taxableIncome,
                personalAllowance = AmountInPence.zero
              )
            ) { result =>
              result.taxableGainOrNetLoss.value            shouldBe 500L
              result.taxableIncome.value                   shouldBe 600L
              result.taxDueAtLowerRate.taxableAmount.value shouldBe 400L
              result.taxDueAtHigherRate                    shouldBe TaxableAmountOfMoney(
                expectedHigherRate(taxYear),
                AmountInPence(100L)
              )
            }

          }

          "the taxable gain is equal than the amount to be taxed at the lower rate" in {
            val disposalDetails = zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))
            val taxableIncome   = AmountInPence(500L)

            testOnGainCalculatedTaxDue(
              calculate(
                triageAnswers = triageAnswers,
                disposalDetails = disposalDetails,
                estimatedIncome = taxableIncome,
                personalAllowance = AmountInPence.zero
              )
            ) { result =>
              result.taxableGainOrNetLoss.value            shouldBe 500L
              result.taxableIncome.value                   shouldBe 500L
              result.taxDueAtLowerRate.taxableAmount.value shouldBe 500L
              result.taxDueAtHigherRate                    shouldBe TaxableAmountOfMoney(
                expectedHigherRate(taxYear),
                AmountInPence(0L)
              )
            }
          }
        }

        "the asset type is residential and" when {

          behave like test(AssetType.Residential, _.cgtRateHigherBandResidential)
        }

        "the asset type is non-residential and" when {

          behave like test(AssetType.NonResidential, _.cgtRateHigherBandNonResidential)
        }

        "the user is a trust" in {
          val incomeTaxHigherRateThreshold = AmountInPence(1000L)
          val taxYear                      = sample[TaxYear].copy(incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold)
          val triageAnswers                = sample[CompleteSingleDisposalTriageAnswers].copy(
            disposalDate = sample[DisposalDate].copy(taxYear = taxYear),
            assetType = AssetType.NonResidential
          )

          val disposalDetails = zeroDisposalDetails.copy(disposalPrice = AmountInPence(500L))
          val taxableIncome   = AmountInPence(600L)

          testOnGainCalculatedTaxDue(
            calculate(
              triageAnswers = triageAnswers,
              disposalDetails = disposalDetails,
              estimatedIncome = taxableIncome,
              personalAllowance = AmountInPence.zero,
              isATrust = true
            )
          ) { result =>
            result.taxableGainOrNetLoss.value            shouldBe 500L
            result.taxableIncome.value                   shouldBe 600L
            result.taxDueAtLowerRate.taxableAmount.value shouldBe 0L
            result.taxDueAtHigherRate                    shouldBe TaxableAmountOfMoney(
              taxYear.cgtRateHigherBandNonResidential,
              AmountInPence(500L)
            )
          }

        }

      }

      "calculate the amount of tax due correctly" when {

        "a loss has been made" in {
          val acquisitionDetails = zeroAcquisitionDetails.copy(
            acquisitionPrice = AmountInPence(10L)
          )

          val result = calculate(
            acquisitionDetails = acquisitionDetails
          )

          result                            shouldBe a[NonGainCalculatedTaxDue]
          result.taxableGainOrNetLoss.value shouldBe -10L
          result.amountOfTaxDue.value       shouldBe 0L
        }

        "the return is a nil-return" in {
          val result = calculate()

          result                            shouldBe a[NonGainCalculatedTaxDue]
          result.taxableGainOrNetLoss.value shouldBe 0L
          result.amountOfTaxDue.value       shouldBe 0L
        }

        "a gain has been made" in {
          val incomeTaxHigherRateThreshold = AmountInPence(1000L)
          val lowerBandTaxRate             = BigDecimal("1.23")
          val higherBandTaxRate            = BigDecimal("4.56")

          val taxYear = sample[TaxYear].copy(
            incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold,
            cgtRateLowerBandResidential = lowerBandTaxRate,
            cgtRateHigherBandResidential = higherBandTaxRate
          )

          val triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(
            disposalDate = sample[DisposalDate].copy(taxYear = taxYear),
            assetType = AssetType.Residential
          )

          val disposalDetails = zeroDisposalDetails.copy(disposalPrice = AmountInPence(987L))

          testOnGainCalculatedTaxDue(
            calculate(
              triageAnswers = triageAnswers,
              disposalDetails = disposalDetails,
              estimatedIncome = AmountInPence(123L),
              personalAllowance = AmountInPence.zero
            )
          ) { result =>
            result.taxableGainOrNetLoss.value shouldBe 987L
            result.taxableIncome.value        shouldBe 123L

            result.taxDueAtLowerRate.taxRate             shouldBe lowerBandTaxRate
            // threshold - taxable income (1000-123) = 877 is less  than taxable gain (987)
            result.taxDueAtLowerRate.taxableAmount.value shouldBe 877L
            // 1.23% of 877 = 10.7871 --> rounds down to 10
            result.taxDueAtLowerRate.taxDue().value      shouldBe 10L

            result.taxDueAtHigherRate.taxRate             shouldBe higherBandTaxRate
            result.taxDueAtHigherRate.taxableAmount.value shouldBe 110L
            // 4.56% of 110 = 15.016  --> rounds down to 5
            result.taxDueAtHigherRate.taxDue().value      shouldBe 5L

            result.amountOfTaxDue.value shouldBe 15L
          }
        }

      }

    }

    "calculating taxable gain or loss" must {

      "calculate total gains after reliefs correctly" in {
        val currentGlar                    = AmountInPence(1L)
        val currentAddress                 = sample[UkAddress]
        val (address1, address2, address3) = (sample[UkAddress], sample[UkAddress], sample[UkAddress])
        val previousReturnCalculationData  = List(
          FurtherReturnCalculationData(address1, AmountInPence(1L)),
          FurtherReturnCalculationData(address2, AmountInPence.zero),
          FurtherReturnCalculationData(address3, AmountInPence(-1L))
        )

        val result = service
          .calculateTaxableGainOrLoss(
            TaxableGainOrLossCalculationRequest(
              previousReturnCalculationData,
              currentGlar,
              sample[CompleteExemptionAndLossesAnswers],
              currentAddress
            )
          )

        result.totalGainsAfterReliefs shouldBe AmountInPence(2L)

        result.calculationData.toSet shouldBe Set(
          FurtherReturnCalculationData(currentAddress, currentGlar),
          FurtherReturnCalculationData(address1, AmountInPence(1L)),
          FurtherReturnCalculationData(address2, AmountInPence.zero),
          FurtherReturnCalculationData(address3, AmountInPence.zero)
        )
      }

      "calculate gain or loss after in year losses correctly" in {
        forAll { (exemptionsAndLosses: CompleteExemptionAndLossesAnswers) =>
          val currentGlar = AmountInPence(1L)
          service
            .calculateTaxableGainOrLoss(
              TaxableGainOrLossCalculationRequest(
                List.empty,
                currentGlar,
                exemptionsAndLosses,
                sample[UkAddress]
              )
            )
            .gainOrLossAfterInYearLosses shouldBe (currentGlar -- exemptionsAndLosses.inYearLosses)
        }
      }

      "calculate year position correctly" when {

        "gain or loss after in year losses is positive and the annual exempt amount is " +
          "less than the gain or loess after in year losses " in {
            val currentGlar         = AmountInPence(10L)
            val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
              inYearLosses = AmountInPence.zero,
              annualExemptAmount = AmountInPence(1L)
            )

            val result = service
              .calculateTaxableGainOrLoss(
                TaxableGainOrLossCalculationRequest(
                  List.empty,
                  currentGlar,
                  exemptionsAndLosses,
                  sample[UkAddress]
                )
              )

            result.gainOrLossAfterInYearLosses shouldBe AmountInPence(10L)
            result.yearPosition                shouldBe AmountInPence(9L)
          }

        "gain or loss after in year losses is positive and the annual exempt amount is " +
          "greater than the gain or loess after in year losses " in {
            val currentGlar         = AmountInPence(10L)
            val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
              inYearLosses = AmountInPence.zero,
              annualExemptAmount = AmountInPence(11L)
            )

            val result = service
              .calculateTaxableGainOrLoss(
                TaxableGainOrLossCalculationRequest(
                  List.empty,
                  currentGlar,
                  exemptionsAndLosses,
                  sample[UkAddress]
                )
              )

            result.gainOrLossAfterInYearLosses shouldBe AmountInPence(10L)
            result.yearPosition                shouldBe AmountInPence.zero
          }

        "gain or loss after in year losses is zero" in {
          val currentGlar         = AmountInPence(10L)
          val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
            inYearLosses = currentGlar,
            annualExemptAmount = AmountInPence(1L)
          )

          val result = service
            .calculateTaxableGainOrLoss(
              TaxableGainOrLossCalculationRequest(
                List.empty,
                currentGlar,
                exemptionsAndLosses,
                sample[UkAddress]
              )
            )

          result.gainOrLossAfterInYearLosses shouldBe AmountInPence.zero
          result.yearPosition                shouldBe AmountInPence.zero
        }

        "gain or loss after in year losses is negative" in {
          val currentGlar         = AmountInPence(10L)
          val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
            inYearLosses = AmountInPence(11L),
            annualExemptAmount = AmountInPence(1L)
          )

          val result = service
            .calculateTaxableGainOrLoss(
              TaxableGainOrLossCalculationRequest(
                List.empty,
                currentGlar,
                exemptionsAndLosses,
                sample[UkAddress]
              )
            )

          result.gainOrLossAfterInYearLosses shouldBe AmountInPence(-1L)
          result.yearPosition                shouldBe AmountInPence(-1L)
        }

      }

      "calculate the taxable gain or loss correctly" when {

        "the year position is positive and the previous years losses is less " +
          "than the year position" in {
            val currentGlar         = AmountInPence(10L)
            val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
              inYearLosses = AmountInPence.zero,
              annualExemptAmount = AmountInPence(1L),
              previousYearsLosses = AmountInPence(2L)
            )

            val result = service
              .calculateTaxableGainOrLoss(
                TaxableGainOrLossCalculationRequest(
                  List.empty,
                  currentGlar,
                  exemptionsAndLosses,
                  sample[UkAddress]
                )
              )

            result.yearPosition       shouldBe AmountInPence(9L)
            result.taxableGainOrLoss  shouldBe AmountInPence(7L)
            result.previousYearLosses shouldBe AmountInPence(2L)

          }

        "the year position is positive and the previous years losses is greater " +
          "than the year position" in {
            val currentGlar         = AmountInPence(10L)
            val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
              inYearLosses = AmountInPence.zero,
              annualExemptAmount = AmountInPence(1L),
              previousYearsLosses = AmountInPence(10L)
            )

            val result = service
              .calculateTaxableGainOrLoss(
                TaxableGainOrLossCalculationRequest(
                  List.empty,
                  currentGlar,
                  exemptionsAndLosses,
                  sample[UkAddress]
                )
              )

            result.yearPosition       shouldBe AmountInPence(9L)
            result.taxableGainOrLoss  shouldBe AmountInPence.zero
            result.previousYearLosses shouldBe AmountInPence(10L)
          }

        "the year position is zero" in {
          val currentGlar         = AmountInPence(10L)
          val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
            inYearLosses = currentGlar,
            annualExemptAmount = AmountInPence(1L),
            previousYearsLosses = AmountInPence(10L)
          )

          val result = service
            .calculateTaxableGainOrLoss(
              TaxableGainOrLossCalculationRequest(
                List.empty,
                currentGlar,
                exemptionsAndLosses,
                sample[UkAddress]
              )
            )

          result.gainOrLossAfterInYearLosses shouldBe AmountInPence.zero
          result.yearPosition                shouldBe AmountInPence.zero
          result.previousYearLosses          shouldBe AmountInPence.zero
        }

        "the year position is negative" in {
          val currentGlar         = AmountInPence(10L)
          val exemptionsAndLosses = sample[CompleteExemptionAndLossesAnswers].copy(
            inYearLosses = AmountInPence(11L),
            annualExemptAmount = AmountInPence(1L),
            previousYearsLosses = AmountInPence(10L)
          )

          val result = service
            .calculateTaxableGainOrLoss(
              TaxableGainOrLossCalculationRequest(
                List.empty,
                currentGlar,
                exemptionsAndLosses,
                sample[UkAddress]
              )
            )

          result.yearPosition       shouldBe AmountInPence(-1L)
          result.yearPosition       shouldBe AmountInPence(-1L)
          result.previousYearLosses shouldBe AmountInPence.zero
        }

      }

    }

    "calculating year to date liability" must {

      def triageAnswers(
        assetType: AssetType = AssetType.Residential,
        cgtRateLowerBandResidential: BigDecimal = BigDecimal(1),
        cgtRateLowerBandNonResidential: BigDecimal = BigDecimal(2),
        cgtRateHigherBandResidential: BigDecimal = BigDecimal(3),
        cgtRateHigherBandNonResidential: BigDecimal = BigDecimal(4),
        incomeTaxHigherRateThreshold: AmountInPence = AmountInPence(100L)
      ): CompleteSingleDisposalTriageAnswers = {
        val taxYear = sample[TaxYear].copy(
          cgtRateLowerBandResidential = cgtRateLowerBandResidential,
          cgtRateLowerBandNonResidential = cgtRateLowerBandNonResidential,
          cgtRateHigherBandResidential = cgtRateHigherBandResidential,
          cgtRateHigherBandNonResidential = cgtRateHigherBandNonResidential,
          incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
        )

        sample[CompleteSingleDisposalTriageAnswers].copy(
          disposalDate = sample[DisposalDate].copy(taxYear = taxYear),
          assetType = assetType
        )
      }

      "calculate taxable income correct" when {

        "the estimated income is greater than the personal allowance" in {
          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(),
            sample[AmountInPence],
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.estimatedIncome   shouldBe AmountInPence(10L)
          result.personalAllowance shouldBe AmountInPence(4L)
          result.taxableIncome     shouldBe AmountInPence(6L)

        }

        "the estimated income is less than the personal allowance" in {
          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(),
            sample[AmountInPence],
            AmountInPence(10L),
            AmountInPence(20L),
            isATrust = false
          )

          service.calculateYearToDateLiability(request).taxableIncome shouldBe AmountInPence.zero
        }

      }

      "calculate the lower band tax correctly" when {

        "a taxable loss has been made" in {
          val cgtRateLowerBandResidential = BigDecimal(6)
          val request                     = YearToDateLiabilityCalculationRequest(
            triageAnswers(cgtRateLowerBandResidential = cgtRateLowerBandResidential),
            AmountInPence(-5L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          service.calculateYearToDateLiability(request).lowerBandTax shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandResidential,
            AmountInPence.zero
          )
        }

        "a taxable gain has been made and is more than the taxable income minus the higher rate threshold" in {
          val cgtRateLowerBandResidential  = BigDecimal(6)
          val incomeTaxHigherRateThreshold = AmountInPence(30L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              cgtRateLowerBandResidential = cgtRateLowerBandResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(50L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(6L)

          result.lowerBandTax shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandResidential,
            AmountInPence(24L)
          )
        }

        "a taxable gain has been made and is less than the taxable income minus the higher rate threshold" in {
          val cgtRateLowerBandResidential  = BigDecimal(6)
          val incomeTaxHigherRateThreshold = AmountInPence(30L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              cgtRateLowerBandResidential = cgtRateLowerBandResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(23L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(6L)

          result.lowerBandTax shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandResidential,
            AmountInPence(23L)
          )
        }

        "a taxable gain has been made and the taxable income is higher than the income tax higher rate threshold" in {
          val cgtRateLowerBandResidential  = BigDecimal(6)
          val incomeTaxHigherRateThreshold = AmountInPence(1L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              cgtRateLowerBandResidential = cgtRateLowerBandResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(50L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(6L)

          result.lowerBandTax shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandResidential,
            AmountInPence.zero
          )
        }

        "the calculation is being done for a trust" in {
          val cgtRateLowerBandResidential  = BigDecimal(6)
          val incomeTaxHigherRateThreshold = AmountInPence(30L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              cgtRateLowerBandResidential = cgtRateLowerBandResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(50L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = true
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(6L)

          result.lowerBandTax shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandResidential,
            AmountInPence.zero
          )
        }

        "the asset type is non-residential" in {
          val cgtRateLowerBandNonResidential = BigDecimal(7)
          val incomeTaxHigherRateThreshold   = AmountInPence(30L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              assetType = AssetType.NonResidential,
              cgtRateLowerBandNonResidential = cgtRateLowerBandNonResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(50L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(6L)

          result.lowerBandTax shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandNonResidential,
            AmountInPence(24L)
          )
        }

      }

      "calculate higher band tax correctly" when {

        "the taxable gain is higher than the lower band taxable amount" in {
          val cgtRateLowerBandResidential  = BigDecimal(6)
          val cgtRateHigherBandResidential = BigDecimal(7)
          val incomeTaxHigherRateThreshold = AmountInPence(30L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              cgtRateLowerBandResidential = cgtRateLowerBandResidential,
              cgtRateHigherBandResidential = cgtRateHigherBandResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(50L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(6L)

          result.lowerBandTax  shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandResidential,
            AmountInPence(24L)
          )
          result.higherBandTax shouldBe TaxableAmountOfMoney(
            cgtRateHigherBandResidential,
            AmountInPence(26L)
          )
        }

        "the taxable loss has been made" in {
          val cgtRateLowerBandResidential  = BigDecimal(6)
          val cgtRateHigherBandResidential = BigDecimal(7)
          val incomeTaxHigherRateThreshold = AmountInPence(35L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              cgtRateLowerBandResidential = cgtRateLowerBandResidential,
              cgtRateHigherBandResidential = cgtRateHigherBandResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(-1L),
            AmountInPence(30L),
            AmountInPence(20L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(10L)

          result.lowerBandTax shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandResidential,
            AmountInPence.zero
          )

          result.higherBandTax shouldBe TaxableAmountOfMoney(
            cgtRateHigherBandResidential,
            AmountInPence.zero
          )
        }

        "the asset type is non residential" in {
          val cgtRateLowerBandNonResidential  = BigDecimal(6)
          val cgtRateHigherBandNonResidential = BigDecimal(7)
          val incomeTaxHigherRateThreshold    = AmountInPence(30L)

          val request = YearToDateLiabilityCalculationRequest(
            triageAnswers(
              assetType = AssetType.NonResidential,
              cgtRateLowerBandNonResidential = cgtRateLowerBandNonResidential,
              cgtRateHigherBandNonResidential = cgtRateHigherBandNonResidential,
              incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
            ),
            AmountInPence(50L),
            AmountInPence(10L),
            AmountInPence(4L),
            isATrust = false
          )

          val result = service.calculateYearToDateLiability(request)
          result.taxableIncome shouldBe AmountInPence(6L)

          result.lowerBandTax  shouldBe TaxableAmountOfMoney(
            cgtRateLowerBandNonResidential,
            AmountInPence(24L)
          )
          result.higherBandTax shouldBe TaxableAmountOfMoney(
            cgtRateHigherBandNonResidential,
            AmountInPence(26L)
          )
        }

      }

      "calculate yearToDateLiability correctly" in {
        val cgtRateLowerBandNonResidential  = BigDecimal(6)
        val cgtRateHigherBandNonResidential = BigDecimal(7)
        val incomeTaxHigherRateThreshold    = AmountInPence(30L)

        val request = YearToDateLiabilityCalculationRequest(
          triageAnswers(
            assetType = AssetType.NonResidential,
            cgtRateLowerBandNonResidential = cgtRateLowerBandNonResidential,
            cgtRateHigherBandNonResidential = cgtRateHigherBandNonResidential,
            incomeTaxHigherRateThreshold = incomeTaxHigherRateThreshold
          ),
          AmountInPence(50L),
          AmountInPence(10L),
          AmountInPence(4L),
          isATrust = false
        )

        val result = service.calculateYearToDateLiability(request)
        result.taxableIncome shouldBe AmountInPence(6L)

        result.lowerBandTax        shouldBe TaxableAmountOfMoney(
          cgtRateLowerBandNonResidential,
          AmountInPence(24L)
        )
        result.higherBandTax       shouldBe TaxableAmountOfMoney(
          cgtRateHigherBandNonResidential,
          AmountInPence(26L)
        )
        result.yearToDateLiability shouldBe (result.lowerBandTax.taxDue() ++ result.higherBandTax.taxDue())
      }

    }

  }

  def testOnGainCalculatedTaxDue(result: CalculatedTaxDue)(f: GainCalculatedTaxDue => Unit): Unit =
    result match {
      case n: NonGainCalculatedTaxDue => fail(s"Expected a gain but got $n")
      case g: GainCalculatedTaxDue    => f(g)
    }

}
