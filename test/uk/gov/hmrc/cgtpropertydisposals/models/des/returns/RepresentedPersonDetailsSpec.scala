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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import java.time.LocalDate

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, NINO, SAUTR}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{DateOfDeath, RepresenteeContactDetails, RepresenteeDetails}

class RepresentedPersonDetailsSpec extends AnyWordSpec with Matchers {

  "RepresentedPersonDetails" must {

    "have an apply method" which {

      "converts from RepresenteeDetails" when {

        "passed a NINO" in {
          val address    = UkAddress("line1", None, None, None, sample[Postcode])
          val desAddress = AddressDetails("line1", None, None, None, Some(address.postcode.value), "GB")

          val answers = sample[CompleteRepresenteeAnswers].copy(
            dateOfDeath = None,
            contactDetails = sample[RepresenteeContactDetails].copy(address = address)
          )

          val nino = sample[NINO]

          RepresentedPersonDetails(RepresenteeDetails(answers, Right(Left(nino)))) shouldBe RepresentedPersonDetails(
            "Capacitor",
            answers.name.firstName,
            answers.name.lastName,
            "NINO",
            nino.value,
            None,
            None,
            None,
            None,
            Some(
              desAddress.copy(
                postalCode = Some(address.postcode.stripAllSpaces)
              )
            ),
            Some(answers.contactDetails.emailAddress.value)
          )
        }

        "passed an SA UTR" in {
          val address    = NonUkAddress("line1", None, None, None, None, sample[Country])
          val desAddress = AddressDetails("line1", None, None, None, None, address.country.code)

          val dateOfDeath = DateOfDeath(LocalDate.now())
          val answers     = sample[CompleteRepresenteeAnswers].copy(
            dateOfDeath = Some(dateOfDeath),
            contactDetails = sample[RepresenteeContactDetails].copy(address = address)
          )

          val sautr = sample[SAUTR]

          RepresentedPersonDetails(RepresenteeDetails(answers, Left(sautr))) shouldBe RepresentedPersonDetails(
            "Personal Representative",
            answers.name.firstName,
            answers.name.lastName,
            "UTR",
            sautr.value,
            None,
            Some(dateOfDeath.value),
            None,
            None,
            Some(desAddress),
            Some(answers.contactDetails.emailAddress.value)
          )
        }

        "passed a cgt reference" in {
          val address    = UkAddress("line1", None, None, None, sample[Postcode])
          val desAddress = AddressDetails("line1", None, None, None, Some(address.postcode.value), "GB")

          val answers = sample[CompleteRepresenteeAnswers].copy(
            dateOfDeath = None,
            contactDetails = sample[RepresenteeContactDetails].copy(address = address)
          )

          val cgtRefrence = sample[CgtReference]

          RepresentedPersonDetails(
            RepresenteeDetails(answers, Right(Right(cgtRefrence)))
          ) shouldBe RepresentedPersonDetails(
            "Capacitor",
            answers.name.firstName,
            answers.name.lastName,
            "ZCGT",
            cgtRefrence.value,
            None,
            None,
            None,
            None,
            Some(
              desAddress.copy(
                postalCode = Some(address.postcode.stripAllSpaces)
              )
            ),
            Some(answers.contactDetails.emailAddress.value)
          )
        }

      }

    }

  }

}
