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

class SourceIdentifierScannerTest
    extends FunSpec
    with IdentifiersGenerators
    with Matchers
    with SqlIdentifiersGenerators {
  case class ClassWithIdentifier(
    sourceIdentifier: SourceIdentifier,
    classWithIdentifiers: List[ClassWithIdentifier] = Nil)
  case class ClassWithIdentifierAndIdentifiedType(
    sourceIdentifier: SourceIdentifier,
    identifiedType: String,
    classWithIdentifiers: List[ClassWithIdentifierAndIdentifiedType] = Nil
  )
  case class ClassWithIdentifierAndCanonicalId(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    classWithIdentifiers: List[ClassWithIdentifierAndCanonicalId] = Nil)
  case class ClassWithIdentifierTypeAndCanonicalId(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    `type`: String,
    classWithIdentifiers: List[ClassWithIdentifierTypeAndCanonicalId] = Nil
  )
  describe("scan") {
    it("retrieves a sourceIdentifiers at the root of the json") {
      val sourceIdentifier = createSourceIdentifier
      val objectWithIdentifier = ClassWithIdentifier(sourceIdentifier)
      SourceIdentifierScanner
        .scan(objectWithIdentifier.asJson)
        .get shouldBe List(sourceIdentifier)
    }

    it("retrieves a sourceIdentifiers nested in the json") {
      val sourceIdentifiers = (1 to 4).map(_ => createSourceIdentifier)
      val objectWithIdentifier = ClassWithIdentifier(
        sourceIdentifiers.head,
        List(
          ClassWithIdentifier(sourceIdentifiers(1)),
          ClassWithIdentifier(
            sourceIdentifiers(2),
            List(ClassWithIdentifier(sourceIdentifiers(3)))))
      )
      SourceIdentifierScanner
        .scan(objectWithIdentifier.asJson)
        .get should contain theSameElementsAs (sourceIdentifiers)
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
  describe("update") {
    it("modifies json to add a single canonicalId in the root") {
      val sourceIdentifier = createSourceIdentifier
      val objectWithIdentifier = ClassWithIdentifier(sourceIdentifier)
      val identifier = Identifier(
        canonicalId = createCanonicalId,
        sourceIdentifier = sourceIdentifier)
      val identified = SourceIdentifierScanner.update(
        objectWithIdentifier.asJson,
        Map(sourceIdentifier -> identifier))
      identified shouldBe a[Success[_]]
      identified.get
        .as[ClassWithIdentifierAndCanonicalId]
        .right
        .get shouldBe ClassWithIdentifierAndCanonicalId(
        sourceIdentifier,
        canonicalId = identifier.CanonicalId)
    }

    it("modifies json to add multiple nested canonicalIds") {
      val sourceIdentifiers = (1 to 4).map(_ => createSourceIdentifier)
      val objectWithIdentifiers = ClassWithIdentifier(
        sourceIdentifiers.head,
        List(
          ClassWithIdentifier(sourceIdentifiers(1)),
          ClassWithIdentifier(
            sourceIdentifiers(2),
            List(ClassWithIdentifier(sourceIdentifiers(3)))))
      )
      val identifiers = sourceIdentifiers.map { sourceIdentifier =>
        sourceIdentifier -> Identifier(createCanonicalId, sourceIdentifier)
      }.toMap
      val canonicalIds =
        sourceIdentifiers.flatMap(identifiers.get).map(_.CanonicalId)
      val identified = SourceIdentifierScanner.update(
        objectWithIdentifiers.asJson,
        identifiers
      )
      identified shouldBe a[Success[_]]
      identified.get
        .as[ClassWithIdentifierAndCanonicalId]
        .right
        .get shouldBe ClassWithIdentifierAndCanonicalId(
        sourceIdentifiers.head,
        canonicalIds.head,
        List(
          ClassWithIdentifierAndCanonicalId(
            sourceIdentifiers(1),
            canonicalIds(1)
          ),
          ClassWithIdentifierAndCanonicalId(
            sourceIdentifiers(2),
            canonicalIds(2),
            List(
              ClassWithIdentifierAndCanonicalId(
                sourceIdentifiers(3),
                canonicalIds(3)
              ))
          )
        )
      )
    }

    it("replaces identifiedType with type") {
      val sourceIdentifier1 = createSourceIdentifier
      val sourceIdentifier2 = createSourceIdentifier
      val objectWithIdentifiers = ClassWithIdentifierAndIdentifiedType(
        sourceIdentifier = sourceIdentifier1,
        identifiedType = "NewType",
        classWithIdentifiers = List(
          ClassWithIdentifierAndIdentifiedType(
            sourceIdentifier = sourceIdentifier2,
            identifiedType = "AnotherNewType"
          )
        )
      )
      val identifiers = Map(
        sourceIdentifier1 -> Identifier(createCanonicalId, sourceIdentifier1),
        sourceIdentifier2 -> Identifier(createCanonicalId, sourceIdentifier2)
      )
      val identified = SourceIdentifierScanner.update(
        objectWithIdentifiers.asJson,
        identifiers
      )
      identified shouldBe a[Success[_]]
      identified.get
        .as[ClassWithIdentifierTypeAndCanonicalId]
        .right
        .get shouldBe ClassWithIdentifierTypeAndCanonicalId(
        sourceIdentifier = sourceIdentifier1,
        canonicalId = identifiers(sourceIdentifier1).CanonicalId,
        `type` = "NewType",
        classWithIdentifiers = List(
          ClassWithIdentifierTypeAndCanonicalId(
            sourceIdentifier = sourceIdentifier2,
            canonicalId = identifiers(sourceIdentifier2).CanonicalId,
            `type` = "AnotherNewType"
          )
        )
      )
    }

    it(
      "fails if it cannot match the identifier to any sourceIdentifier in the json") {
      val sourceIdentifier = createSourceIdentifier
      val otherSourceIdentifier = createSourceIdentifier
      val objectWithIdentifier = ClassWithIdentifier(sourceIdentifier)
      val identifier =
        createSQLIdentifierWith(sourceIdentifier = otherSourceIdentifier)
      val identified = SourceIdentifierScanner.update(
        objectWithIdentifier.asJson,
        Map(otherSourceIdentifier -> identifier))
      identified shouldBe a[Failure[_]]
    }
  }
}
