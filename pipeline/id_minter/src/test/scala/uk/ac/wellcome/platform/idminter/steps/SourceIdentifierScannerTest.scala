package uk.ac.wellcome.platform.idminter.steps

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import uk.ac.wellcome.models.work.internal.SourceIdentifier

import scala.util.Failure

class SourceIdentifierScannerTest extends FunSpec with IdentifiersGenerators with Matchers {
  case class ClassWithIdentifier(sourceIdentifier: SourceIdentifier,
                                 classWithIdentifiers: List[ClassWithIdentifier] =Nil
                                )
  it("retrieves a sourceIdentifiers at the root of the json"){
    val sourceIdentifier = createSourceIdentifier
    val objectWithIdentifier = ClassWithIdentifier(sourceIdentifier)
    SourceIdentifierScanner.scan(objectWithIdentifier.asJson).get shouldBe List(sourceIdentifier)
  }

  it("retrieves a sourceIdentifiers nested in the json"){

    val sourceIdentifiers = (1 to 4).map (_ => createSourceIdentifier)
    val objectWithIdentifier = ClassWithIdentifier(sourceIdentifiers.head,
      List(ClassWithIdentifier(sourceIdentifiers(1)),
        ClassWithIdentifier(sourceIdentifiers(2), List(ClassWithIdentifier(sourceIdentifiers(3)))))
    )
    SourceIdentifierScanner.scan(objectWithIdentifier.asJson).get should contain theSameElementsAs(sourceIdentifiers)
  }

  it("throws an exception if it cannot parse a sourceIdentifier"){
    val json =
      """
        |{
        | "sourceIdentifier": {
        |   "something": "something"
        | }
        |}
        |""".stripMargin

      SourceIdentifierScanner.scan(parse(json).right.get) shouldBe a[Failure[_]]

  }
}
