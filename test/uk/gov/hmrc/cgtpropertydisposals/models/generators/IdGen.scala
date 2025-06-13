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

import org.scalacheck.Gen
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{AgentReferenceNumber, CgtReference, NINO, SAUTR, SapNumber}
import io.github.martinhh.derived.scalacheck.given

object IdGen extends GenUtils {

  given cgtReferenceGen: Gen[CgtReference] = gen[CgtReference]

  given sapNumberGen: Gen[SapNumber] = gen[SapNumber]

  given sautrGen: Gen[SAUTR] = gen[SAUTR]

  given ninoGen: Gen[NINO] = gen[NINO]

  given agentReferenceNumberGen: Gen[AgentReferenceNumber] = gen[AgentReferenceNumber]
}
