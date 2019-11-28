package uk.gov.hmrc.cgtpropertydisposals.models

import org.scalatest.{Matchers, WordSpec}

class UUIDGeneratorImplSpec extends WordSpec with Matchers {

  "UUIDIDGeneratorImpl" must {

    "generate random UUID's" in {
      val generator = new UUIDGeneratorImpl()

      val list = List.fill(100)(generator.nextId())
      list.distinct shouldBe list
    }

  }

}
