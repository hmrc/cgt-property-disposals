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

package uk.gov.hmrc.cgtpropertydisposals.controllers

import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait ControllerSpec extends AnyWordSpec with Matchers with IdiomaticMockito {

//  val overrideBindings: List[GuiceableModule] = List.empty[GuiceableModule]

//  def buildFakeApplication(): Application =
////    val metricsBinding: GuiceableModule = bind[Metrics].toInstance(MockMetrics.metrics)
//    new GuiceApplicationBuilder()
//      .configure(
//        Configuration(
//          ConfigFactory.parseString(
//            """
//              | metrics.jvm = false
//              | metrics.logback = false
//          """.stripMargin
//          )
//        )
//      )
////      .bindings(new DmsSubmissionModule(mock[Environment], mock[Configuration]))
////      .overrides(metricsBinding :: overrideBindings: _*)
//      .build()

//  lazy val fakeApplication: Application = buildFakeApplication()

//  def instanceOf[A : ClassTag]: A = fakeApplication.injector.instanceOf[A]

//  abstract override def beforeAll(): Unit = {
//    Play.start(fakeApplication)
//    super.beforeAll()
//  }
//
//  override def afterAll(): Unit = {
//    Play.stop(fakeApplication)
//    super.afterAll()
//  }
}
