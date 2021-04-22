package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SierraItemsOnOrderTest extends AnyFunSpec with Matchers {
  it("returns nothing if there are no orders or items") {
    true shouldBe false
  }

  describe("returns 'on order' items") {
    it("if there are orders with status 'o' and no RDATE") {
      true shouldBe false
    }

    it("unless there are any items") {
      true shouldBe false
    }
  }

  describe("returns 'awaiting cataloguing' items") {
    it("if there are orders with status 'a' and an RDATE") {
      true shouldBe false
    }

    it("unless there are any items") {
      true shouldBe false
    }
  }

  describe("skips unrecognised order records") {
    it("no RDATE, unrecognised status") {
      true shouldBe false
    }

    it("RDATE, unrecognised status") {
      true shouldBe false
    }
  }
}
