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

import org.scalacheck.{Arbitrary, Gen}
import io.github.martinhh.derived.scalacheck.given
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnResponse, SubmitReturnWrapper}

object SubmitReturnGen extends GenUtils {
  given submitReturnRequestGen: Gen[SubmitReturnRequest] = gen[SubmitReturnRequest]

  given submitReturnWrapperGen: Gen[SubmitReturnWrapper] = for {
    id          <- Gen.uuid.map(_.toString)
    request     <- Arbitrary.arbitrary[SubmitReturnRequest]
    lastUpdated <- Arbitrary.arbitrary[java.time.Instant]
  } yield SubmitReturnWrapper(id, request, lastUpdated)

  given submitReturnResponseGen: Gen[SubmitReturnResponse] = gen[SubmitReturnResponse]

}
