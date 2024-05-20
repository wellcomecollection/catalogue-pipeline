package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.xml.{Attribute, Null, Text}

object DatableObject extends Datable

class DatableTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  describe("datable elements") {
    it("returns None if there are no datable attributes") {
      val result = DatableObject.formatDatablePrefix(
        <x>Owned by Herb Wells</x>
      )
      result shouldBe None
    }

    it("ignores empty datable attributes") {

      val tests = Table(
        ("element", "result"),
        (<x from="">Owned by James Cole</x>, None),
        (
          <x from="" to="1901-12-25">Owned by James Cole</x>,
          Some("(to 1901-12-25)")
        )
      )

      forAll(tests) {
        case (element, result) =>
          DatableObject.formatDatablePrefix(element) shouldBe result
      }
    }

    it("includes all date bounds in a logical order") {
      val result = DatableObject.formatDatablePrefix(
        <x notAfter="1745" from="1901-12-25" to="1930-01-01" notBefore="1876" when="2022-07-29" >Owned by Emmet Brown</x>
      )
      result.get shouldBe
        "(2022-07-29, from 1901-12-25, not before 1876, to 1930-01-01, not after 1745)"
    }

    it("transforms a single datable attribute") {
      val undated = <x>
        Owned by Marty McFly
      </x>

      val tests = Table(
        ("element", "result"),
        (
          undated % Attribute(None, "when", Text("1901-12-25"), Null),
          "(1901-12-25)"
        ),
        (
          undated % Attribute(None, "from", Text("1901-12-25"), Null),
          "(from 1901-12-25)"
        ),
        (
          undated % Attribute(None, "to", Text("1901-12-25"), Null),
          "(to 1901-12-25)"
        ),
        (
          undated % Attribute(None, "notBefore", Text("1901-12-25"), Null),
          "(not before 1901-12-25)"
        ),
        (
          undated % Attribute(None, "notAfter", Text("1901-12-25"), Null),
          "(not after 1901-12-25)"
        )
      )

      forAll(tests) {
        case (element, result) =>
          DatableObject.formatDatablePrefix(element).get shouldBe result
      }
    }
  }
}
