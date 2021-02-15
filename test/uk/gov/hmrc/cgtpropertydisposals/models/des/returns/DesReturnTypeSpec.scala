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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import java.time.{Clock, Instant, ZoneId}

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsObject, JsString, JsSuccess, JsValue, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.AgentReferenceNumber
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AmendReturnData, CompleteReturnWithSummary, ReturnSummary, SubmitReturnRequest}

class DesReturnTypeSpec extends WordSpec with Matchers {

  "DesReturnType" must {

    "a format instance" which {

      "writes JSON properly" in {
        def test(r: DesReturnType, j: JsValue) = Json.toJson(r) shouldBe j

        test(
          CreateReturnType("source"),
          JsObject(Map("source" -> JsString("source"), "submissionType" -> JsString("New")))
        )

        test(
          AmendReturnType("source", Some("id")),
          JsObject(
            Map("source" -> JsString("source"), "submissionType" -> JsString("Amend"), "submissionID" -> JsString("id"))
          )
        )
      }

      "reads JSON properly" in {
        Json
          .parse(
            """
            |{
            | "source": "source",
            | "submissionType" : "New"
            |}
            |""".stripMargin
          )
          .validate[DesReturnType] shouldBe JsSuccess(CreateReturnType("source"))

        Json
          .parse(
            """
              |{
              | "source": "source",
              | "submissionType" : "Amend"
              |}
              |""".stripMargin
          )
          .validate[DesReturnType] shouldBe JsSuccess(AmendReturnType("source", None))
      }

    }

    "an apply method" which {

      val instant   = Instant.now()
      val clock     = Clock.fixed(instant, ZoneId.of("Z"))
      val timestamp = instant.getEpochSecond

      "handles individual new returns" in {
        val submitReturnRequest = sample[SubmitReturnRequest].copy(
          isFurtherReturn = false,
          amendReturnData = None,
          agentReferenceNumber = None
        )
        DesReturnType(submitReturnRequest, clock) shouldBe CreateReturnType("self digital")
      }

      "handles agent new returns" in {
        val submitReturnRequest = sample[SubmitReturnRequest].copy(
          isFurtherReturn = false,
          amendReturnData = None,
          agentReferenceNumber = Some(sample[AgentReferenceNumber])
        )
        DesReturnType(submitReturnRequest, clock) shouldBe CreateReturnType("agent digital")
      }

      "handles further returns" in {
        val submitReturnRequest = sample[SubmitReturnRequest].copy(
          isFurtherReturn = true,
          amendReturnData = None,
          agentReferenceNumber = None
        )
        DesReturnType(submitReturnRequest, clock) shouldBe CreateReturnType(s"self digital $timestamp")
      }

      "handles amend returns" in {
        val formBundleId        = "formBundleId"
        val submitReturnRequest = sample[SubmitReturnRequest].copy(
          isFurtherReturn = true,
          amendReturnData = Some(
            sample[AmendReturnData].copy(
              originalReturn = sample[CompleteReturnWithSummary].copy(
                summary = sample[ReturnSummary].copy(submissionId = formBundleId)
              )
            )
          ),
          agentReferenceNumber = Some(sample[AgentReferenceNumber])
        )

        DesReturnType(submitReturnRequest, clock) shouldBe AmendReturnType(
          s"agent digital $timestamp",
          Some(formBundleId)
        )
      }

    }

  }

}
