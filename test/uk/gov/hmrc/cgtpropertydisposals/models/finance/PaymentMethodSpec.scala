/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.finance

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cgtpropertydisposals.models.finance.PaymentMethod._

class PaymentMethodSpec extends AnyWordSpec with Matchers {

  "PaymentMethod" must {

    "have a method which converts strings to payment methods" in {
      PaymentMethod.fromString("BACS RECEIPTS")                  shouldBe Right(BACS)
      PaymentMethod.fromString("CHAPS")                          shouldBe Right(CHAPS)
      PaymentMethod.fromString("PAYMENTS MADE BY CHEQUE")        shouldBe Right(Cheque)
      PaymentMethod.fromString("TPS RECEIPTS BY DEBIT CARD")     shouldBe Right(DebitCardByTelephone)
      PaymentMethod.fromString("BILLPAY/OLPG/GIROBANK")          shouldBe Right(PTAOnlineWorldpayDebitCardPayment)
      PaymentMethod.fromString("CREDIT FOR INTERNET RECEIPTS")   shouldBe Right(PTAOnlineWorldpayCreditCardPayment)
      PaymentMethod.fromString("BANK GIRO RECEIPTS")             shouldBe Right(GIROReceipts)
      PaymentMethod.fromString("BANK GIRO IN CREDITS")           shouldBe Right(GIROCredits)
      PaymentMethod.fromString("CHEQUE RECEIPTS")                shouldBe Right(ChequeReceipts)
      PaymentMethod.fromString("TPS RECEIPTS BY CREDIT CARD")    shouldBe Right(CreditCardByTelephone)
      PaymentMethod.fromString("NATIONAL DIRECT DEBIT RECEIPTS") shouldBe Right(DirectDebit)
      PaymentMethod.fromString("FPS RECEIPTS")                   shouldBe Right(FasterPayment)
      PaymentMethod.fromString("GIROBANK RECEIPTS")              shouldBe Right(GiroBankReceipts)
      PaymentMethod.fromString("GIROBANK/ POST OFFICE")          shouldBe Right(GiroBankPostOffice)
      PaymentMethod.fromString("PAYMASTER")                      shouldBe Right(Paymaster)
      PaymentMethod.fromString("BANK LODGEMENT PAYMENT")         shouldBe Right(BankLodgement)
      PaymentMethod.fromString("INCENTIVE")                      shouldBe Right(Incentive)
      PaymentMethod.fromString("LOCAL OFFICE PAYMENTS")          shouldBe Right(LocalOfficePayment)
      PaymentMethod.fromString("NIL DECLARATIONS")               shouldBe Right(NilDeclarations)
      PaymentMethod.fromString("OVERPAYMENTS TO DUTY")           shouldBe Right(OverpaymentsToDuty)
      PaymentMethod.fromString("REALLOCATION FROM OAS TO DUTY")  shouldBe Right(ReallocationFromOASToDuty)
      PaymentMethod.fromString("PAYMENT NOT EXPECTED")           shouldBe Right(PaymentNotExpected)
      PaymentMethod.fromString("REALLOCATION")                   shouldBe Right(Reallocation)
      PaymentMethod.fromString("REPAYMENT INTEREST ALLOCATED")   shouldBe Right(RepaymentInterestAllocated)
      PaymentMethod.fromString("VOLUNTARY DIRECT PAYMENTS")      shouldBe Right(VoluntaryDirectPayments)
      PaymentMethod.fromString("abc").isLeft                     shouldBe true

    }

  }

}
