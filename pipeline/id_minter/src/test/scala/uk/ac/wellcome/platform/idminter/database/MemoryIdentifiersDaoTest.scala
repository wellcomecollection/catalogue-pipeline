package uk.ac.wellcome.platform.idminter.database

import org.scalatest.{EitherValues, FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.IdentifierType
import uk.ac.wellcome.platform.idminter.fixtures.IdMinterGenerators
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.storage.{DaoWriteError, DoesNotExistError}

class MemoryIdentifiersDaoTest
    extends FunSpec
    with Matchers
    with IdentifiersGenerators
    with IdMinterGenerators
    with EitherValues {
  describe("get") {
    it("gets an Identifier if it finds a matching SourceSystem and SourceId") {
      val sourceIdentifier = createSourceIdentifier
      val identifier = createIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier) shouldBe a[Right[_, _]]

      dao.get(sourceIdentifier).right.value shouldBe identifier
    }

    it("finds no identifier if the source identifier value is different") {
      val identifier = createIdentifier

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier) shouldBe a[Right[_, _]]

      val sourceIdentifier = toSourceIdentifier(identifier).copy(
        value = "not_an_existing_value"
      )

      dao.get(sourceIdentifier).left.value shouldBe a[DoesNotExistError]
    }

    it("finds no identifier if the source identifier type is different") {
      val identifier: Identifier = createIdentifierWith(
        sourceIdentifier = createSourceIdentifierWith(
          identifierType = IdentifierType("miro-image-number")
        )
      )

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier) shouldBe a[Right[_, _]]

      val sourceIdentifier = toSourceIdentifier(identifier).copy(
        identifierType = IdentifierType("sierra-system-number")
      )

      dao.get(sourceIdentifier).left.value shouldBe a[DoesNotExistError]
    }

    it("finds no identifier if the ontology type is different") {
      val identifier: Identifier = createIdentifierWith(
        sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Agent"
        )
      )

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier) shouldBe a[Right[_, _]]

      val sourceIdentifier = toSourceIdentifier(identifier).copy(
        ontologyType = "Item"
      )

      dao.get(sourceIdentifier).left.value shouldBe a[DoesNotExistError]
    }
  }

  describe("put") {
    it("fails to insert a record with a duplicate CanonicalId") {
      val identifier = createIdentifier
      val duplicateIdentifier = createIdentifierWith(
        canonicalId = identifier.CanonicalId
      )

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier) shouldBe a[Right[_, _]]

      val putResult = dao.put(duplicateIdentifier)
      putResult.left.value shouldBe a[DaoWriteError]
      putResult.left.value.e shouldBe a[Throwable]
    }

    it(
      "saves records with the same SourceSystem and SourceId but different OntologyType") {
      val sourceIdentifier1 = createSourceIdentifierWith(
        ontologyType = "Foo"
      )
      val sourceIdentifier2 = createSourceIdentifierWith(
        ontologyType = "Bar"
      )

      val identifier1 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier1) shouldBe a[Right[_, _]]
      dao.put(identifier2) shouldBe a[Right[_, _]]
    }

    it(
      "saves records with different SourceId but the same OntologyType and SourceSystem") {
      val sourceIdentifier1 = createSourceIdentifierWith(
        value = "1234"
      )
      val sourceIdentifier2 = createSourceIdentifierWith(
        value = "5678"
      )

      val identifier1 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier1) shouldBe a[Right[_, _]]
      dao.put(identifier2) shouldBe a[Right[_, _]]
    }

    it(
      "does not insert records with the same SourceId, SourceSystem and OntologyType") {
      val sourceIdentifier = createSourceIdentifier

      val identifier1 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )
      val identifier2 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      val dao = new MemoryIdentifiersDao()

      dao.put(identifier1) shouldBe a[Right[_, _]]

      val triedSave = dao.put(identifier2)
      triedSave.left.value shouldBe a[DaoWriteError]
      triedSave.left.value.e shouldBe a[Throwable]
    }
  }
}
