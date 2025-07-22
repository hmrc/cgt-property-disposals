/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.generators

import io.github.martinhh.derived.scalacheck.given
import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}

trait AddressHigherPriorityGen {
  implicit val postcodeGen: Gen[Postcode] = Generators.stringGen.map(Postcode(_))

  implicit val ukAddressGen: Gen[UkAddress] =
    for {
      line1    <- Generators.stringGen
      line2    <- Gen.option(Generators.stringGen)
      town     <- Gen.option(Generators.stringGen)
      county   <- Gen.option(Generators.stringGen)
      postcode <- postcodeGen
    } yield UkAddress(line1, line2, town, county, postcode)
}

object AddressGen extends AddressLowerPriorityGen {

  given addressGen: Gen[Address] = gen[Address]

  given postcodeGen: Gen[Postcode] = Gen.oneOf(List(Postcode("BN11 3QY"), Postcode("BN11 4QY")))

  given ukAddressGen: Gen[UkAddress] =
    for {
      a <- gen[UkAddress]
      p <- postcodeGen
    } yield a.copy(postcode = p)

  given countryGen: Gen[Country] = Gen.oneOf(Country.countryCodes.map(Country(_)))
}

trait AddressLowerPriorityGen extends GenUtils {
  given nonUkAddressGen: Gen[NonUkAddress] = gen[NonUkAddress]
}
