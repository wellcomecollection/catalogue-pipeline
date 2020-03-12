package uk.ac.wellcome.platform.idminter.steps

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.fixtures.SqlIdentifiersGenerators
import uk.ac.wellcome.platform.idminter.models.Identifier

import scala.util.{Failure, Success}

class SourceIdentifierScannerTest extends FunSpec with IdentifiersGenerators with Matchers with SqlIdentifiersGenerators {
  case class ClassWithIdentifier(sourceIdentifier: SourceIdentifier,
                                 classWithIdentifiers: List[ClassWithIdentifier] =Nil
                                )
  case class ClassWithIdentifierAndCanonicalId(sourceIdentifier: SourceIdentifier,
                                               canonicalId: String,
                                 classWithIdentifiers: List[ClassWithIdentifierAndCanonicalId] =Nil
                                )
  describe("scan") {
    it("retrieves a sourceIdentifiers at the root of the json") {
      val sourceIdentifier = createSourceIdentifier
      val objectWithIdentifier = ClassWithIdentifier(sourceIdentifier)
      SourceIdentifierScanner.scan(objectWithIdentifier.asJson).get shouldBe List(sourceIdentifier)
    }

    it("retrieves a sourceIdentifiers nested in the json") {

      val sourceIdentifiers = (1 to 4).map(_ => createSourceIdentifier)
      val objectWithIdentifier = ClassWithIdentifier(sourceIdentifiers.head,
        List(ClassWithIdentifier(sourceIdentifiers(1)),
          ClassWithIdentifier(sourceIdentifiers(2), List(ClassWithIdentifier(sourceIdentifiers(3)))))
      )
      SourceIdentifierScanner.scan(objectWithIdentifier.asJson).get should contain theSameElementsAs (sourceIdentifiers)
    }

    it("throws an exception if it cannot parse a sourceIdentifier") {
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
  describe("update"){
    it("modifies a json to add a single canonicalId"){
      val sourceIdentifier = createSourceIdentifier
      val objectWithIdentifier = ClassWithIdentifier(sourceIdentifier)
      val identifier = Identifier(canonicalId = createCanonicalId, sourceIdentifier = sourceIdentifier)
      val identified = SourceIdentifierScanner.update(objectWithIdentifier.asJson, Map(sourceIdentifier->identifier))
      identified shouldBe a[Success[_]]
      identified.get.as[ClassWithIdentifierAndCanonicalId].right.get shouldBe ClassWithIdentifierAndCanonicalId(sourceIdentifier, canonicalId = identifier.CanonicalId)
    }

    it("fails if it cannot match the identifier to any sourceIdentifier in the json"){
      val sourceIdentifier = createSourceIdentifier
      val otherSourceIdentifier = createSourceIdentifier
      val objectWithIdentifier = ClassWithIdentifier(sourceIdentifier)
      val identifier = createSQLIdentifierWith(sourceIdentifier= otherSourceIdentifier)
      val identified = SourceIdentifierScanner.update(objectWithIdentifier.asJson, Map(otherSourceIdentifier -> identifier))
      identified shouldBe a[Failure[_]]
    }
  }
}
