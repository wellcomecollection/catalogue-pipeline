package uk.ac.wellcome.platform.idminter.database

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.storage.store.memory.MemoryStore

import scala.util.{Failure, Success}

class IdentifiersDaoTest
    extends FunSpec
    with Matchers
    with IdentifiersGenerators {

  def withIdentifiersDao[R](
    testWith: TestWith[IdentifiersDao[MemoryStore[SourceIdentifier, Identifier]], R]): R  = {

      val memoryStore =
        new MemoryStore[SourceIdentifier, Identifier](Map.empty)

      val identifiersDao = new IdentifiersDao(memoryStore)

      testWith(identifiersDao)
    }

  describe("lookupID") {
    it("gets an Identifier if it finds a matching SourceSystem and SourceId") {
      val sourceIdentifier = createSourceIdentifier
      val identifier = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      withIdentifiersDao { identifiersDao =>
          identifiersDao.saveIdentifier(sourceIdentifier, identifier) shouldBe Success(1)

          val triedLookup = identifiersDao.lookupId(
            sourceIdentifier = sourceIdentifier
          )

          triedLookup shouldBe Success(Some(identifier))
      }
    }

    it(
      "does not get an identifier if there is no matching SourceSystem and SourceId") {
      val (sourceIdentifier, identifier) = createSQLIdentifier

      withIdentifiersDao { identifiersDao =>
          identifiersDao.saveIdentifier(sourceIdentifier, identifier) shouldBe a[Success[_]]

          val unknownSourceIdentifier = createSourceIdentifierWith(
            ontologyType = identifier.OntologyType,
            value = "not_an_existing_value"
          )

          val triedLookup = identifiersDao.lookupId(
            sourceIdentifier = unknownSourceIdentifier
          )

          triedLookup shouldBe Success(None)
      }
    }
  }

  describe("saveIdentifier") {
    it("adds the provided identifier into the database") {
      val (sourceIdentifier, identifier) = createSQLIdentifier

      withIdentifiersDao { identifiersDao =>
          identifiersDao.saveIdentifier(sourceIdentifier, identifier)

          // TODO: introspect dynamo
          true shouldBe false
      }
    }

    it("fails to insert a record with a duplicate CanonicalId") {
      val (sourceIdentifier, identifier) = createSQLIdentifier
      val duplicateIdentifier = createSQLIdentifierWith(
        canonicalId = identifier.CanonicalId
      )

      withIdentifiersDao { identifiersDao =>
          identifiersDao.saveIdentifier(
            sourceIdentifier,
            identifier
          ) shouldBe a[Success[_]]

          val triedSave = identifiersDao.saveIdentifier(
            sourceIdentifier,
            duplicateIdentifier
          )

          triedSave shouldBe a[Failure[_]]
          triedSave.failed.get shouldBe a[IdMinterException]
      }
    }

    it(
      "saves records with the same SourceSystem and SourceId but different OntologyType") {
      val sourceIdentifier1 = createSourceIdentifierWith(
        ontologyType = "Foo"
      )
      val sourceIdentifier2 = createSourceIdentifierWith(
        ontologyType = "Bar"
      )

      val identifier1 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao { identifiersDao =>
          identifiersDao.saveIdentifier(sourceIdentifier1, identifier1) shouldBe a[Success[_]]
          identifiersDao.saveIdentifier(sourceIdentifier2, identifier2) shouldBe a[Success[_]]
      }
    }

    it(
      "saves records with different SourceId but the same OntologyType and SourceSystem") {
      val sourceIdentifier1 = createSourceIdentifierWith(
        value = "1234"
      )
      val sourceIdentifier2 = createSourceIdentifierWith(
        value = "5678"
      )

      val identifier1 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao { identifiersDao =>
          identifiersDao.saveIdentifier(sourceIdentifier1, identifier1) shouldBe a[Success[_]]
          identifiersDao.saveIdentifier(sourceIdentifier2, identifier2) shouldBe a[Success[_]]
      }
    }

    it(
      "does not insert records with the same SourceId, SourceSystem and OntologyType") {
      val sourceIdentifier = createSourceIdentifier

      val identifier1 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )
      val identifier2 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      withIdentifiersDao { identifiersDao =>
          identifiersDao.saveIdentifier(sourceIdentifier, identifier1) shouldBe a[Success[_]]

          val triedSave = identifiersDao.saveIdentifier(sourceIdentifier, identifier2)

          triedSave shouldBe a[Failure[_]]
          triedSave.failed.get shouldBe a[IdMinterException]
      }
    }
  }

  def createSQLIdentifierWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier
  ): Identifier =
    Identifier(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier
    )

  def createSQLIdentifier: (SourceIdentifier, Identifier) = {
    val sourceIdentifier = createSourceIdentifier
    val sqlIdentifier = createSQLIdentifierWith(sourceIdentifier = sourceIdentifier)

    (sourceIdentifier, sqlIdentifier)
  }
}
