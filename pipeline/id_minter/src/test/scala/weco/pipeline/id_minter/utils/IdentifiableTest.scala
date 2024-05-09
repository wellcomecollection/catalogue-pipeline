package weco.pipeline.id_minter.utils

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class IdentifiableTest extends AnyFunSpec with Matchers {

  it("generates an 8-char string, with no ambiguous characters") {
    (1 to 100).map {
      _ =>
        val id = Identifiable.generate.underlying
        id should have size 8
        id.toCharArray should contain noneOf ('0', 'o', 'i', 'l', '1')
        id should fullyMatch regex "[0-9|a-z&&[^oil10]]{8}"
    }
  }

  it("never generates an identifier that starts with a number") {
    (1 to 100).map {
      _ =>
        Identifiable.generate.underlying should not(startWith regex "[0-9]")
    }
  }
}
