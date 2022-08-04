package weco.pipeline.id_minter.database

import java.sql.BatchUpdateException
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scalikejdbc._
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.pipeline.id_minter.fixtures.{
  IdentifiersDatabase,
  SqlIdentifiersGenerators
}
import weco.pipeline.id_minter.models.Identifier

import scala.util.{Failure, Success}

class IdentifiersDaoTest
    extends AnyFunSpec
    with IdentifiersDatabase
    with Matchers
    with SqlIdentifiersGenerators
    with TableDrivenPropertyChecks {

  implicit val session: DBSession = NamedAutoSession('primary)

  describe("lookupIds") {

    it("retrieves a single id from the identifiers database") {
      val sourceIdentifier = createSourceIdentifier
      val identifier = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      withIdentifiersDao(existingEntries = Seq(identifier)) {
        case (identifiersDao, _) =>
          val triedLookup = identifiersDao.lookupIds(List(sourceIdentifier))

          triedLookup shouldBe a[Success[_]]
          triedLookup.get.existingIdentifiers shouldBe Map(
            sourceIdentifier -> identifier)
          triedLookup.get.unmintedIdentifiers shouldBe empty
      }
    }

    it("retrieves multiple ids from the identifiers database") {
      val identifiersMap = (1 to 3).map { _ =>
        val sourceIdentifier = createSourceIdentifier
        val identifier =
          createSQLIdentifierWith(sourceIdentifier = sourceIdentifier)
        (sourceIdentifier, identifier)
      }.toMap

      withIdentifiersDao(existingEntries = identifiersMap.values.toSeq) {
        case (identifiersDao, _) =>
          val triedLookup = identifiersDao.lookupIds(identifiersMap.keys.toList)

          triedLookup shouldBe a[Success[_]]
          triedLookup.get.existingIdentifiers shouldBe identifiersMap
          triedLookup.get.unmintedIdentifiers shouldBe empty
      }
    }

    it(
      "matches ids based on all fields on the SourceIdentifier (same value, different ontology type)") {
      val workSourceIdentifier = createSourceIdentifierWith(
        ontologyType = "Work"
      )
      val itemSourceIdentifier = workSourceIdentifier.copy(
        ontologyType = "Item"
      )
      val sourceIdentifiers =
        List(workSourceIdentifier, itemSourceIdentifier)
      val identifiers = sourceIdentifiers.map(sourceIdentifier =>
        createSQLIdentifierWith(sourceIdentifier = sourceIdentifier))

      withIdentifiersDao(existingEntries = identifiers) {
        case (identifiersDao, _) =>
          val triedLookup = identifiersDao.lookupIds(sourceIdentifiers)

          triedLookup shouldBe a[Success[_]]
          triedLookup.get.existingIdentifiers shouldBe sourceIdentifiers
            .zip(identifiers)
            .toMap
          triedLookup.get.unmintedIdentifiers shouldBe empty
      }
    }

    it("raises an exception if an unexpected id is returned") {
      val testCases = Table(
        "identifierType",
        IdentifierType.MiroImageNumber,
        IdentifierType.SierraSystemNumber,
        IdentifierType.CalmRecordIdentifier
      )
      forAll(testCases) { identifierType =>
        val sourceIdentifier = createSourceIdentifierWith(
          value = "deadbeef",
          identifierType = identifierType)
        val identifier = createSQLIdentifierWith(
          sourceIdentifier = sourceIdentifier
        )
        val badCaseIdentifier = sourceIdentifier.copy(value = "DEADBEEF")
        withIdentifiersDao(existingEntries = Seq(identifier)) {
          case (identifiersDao, _) =>
            val triedLookup = identifiersDao.lookupIds(List(badCaseIdentifier))
            triedLookup match {
              case Failure(e) =>
                e.getMessage.strip shouldBe s"The query returned a sourceIdentifier (Work[${identifierType.id}/deadbeef]) which we weren't looking for (List(Work[${identifierType.id}/DEADBEEF]))"
              case unexpected =>
                fail(s"lookupIds should have failed: $unexpected")
            }
            triedLookup shouldBe a[Failure[_]]

        }
      }
    }

    it("raises a special exception if a wrong-case METS id is used") {
      val identifierType = IdentifierType.METS
      val sourceIdentifier = createSourceIdentifierWith(
        value = "B12345",
        identifierType = identifierType)
      val identifier = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )
      val badCaseIdentifier = sourceIdentifier.copy(value = "b12345")
      withIdentifiersDao(existingEntries = Seq(identifier)) {
        case (identifiersDao, _) =>
          val triedLookup = identifiersDao.lookupIds(List(badCaseIdentifier))
          triedLookup match {
            case Failure(e) =>
              e.getMessage should include(
                "but it did return a similar identifier")
            case unexpected =>
              fail(s"lookupIds should have failed: $unexpected")
          }
          triedLookup shouldBe a[Failure[_]]

      }
    }

    it("retrieves label derived identifiers case-insensitively") {
      val sourceIdentifier = createSourceIdentifierWith(
        identifierType = IdentifierType.LabelDerived,
        value = "bAnAnA"
      )
      val identifier = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )
      val badCaseIdentifier = sourceIdentifier.copy(value = "BaNaNa")

      withIdentifiersDao(existingEntries = Seq(identifier)) {
        case (identifiersDao, _) =>
          val triedLookup = identifiersDao.lookupIds(List(badCaseIdentifier))

          triedLookup shouldBe a[Success[_]]
          triedLookup.get.existingIdentifiers shouldBe Map(
            badCaseIdentifier -> identifier)
          triedLookup.get.unmintedIdentifiers shouldBe empty
      }
    }

    it("puts the IDs that aren't in the DAO in the unmintedIdentifiers field") {
      val identifiersMap = (1 to 3).map { _ =>
        val sourceIdentifier = createSourceIdentifier
        val identifier =
          createSQLIdentifierWith(sourceIdentifier = sourceIdentifier)
        (sourceIdentifier, identifier)
      }.toMap
      val unmintedSourceIdentifiers =
        (1 to 2).map(_ => createSourceIdentifier).toList

      withIdentifiersDao(existingEntries = identifiersMap.values.toSeq) {
        case (identifiersDao, _) =>
          val triedLookup = identifiersDao.lookupIds(
            identifiersMap.keys.toList ++ unmintedSourceIdentifiers
          )

          triedLookup shouldBe a[Success[_]]

          triedLookup.get.existingIdentifiers shouldBe identifiersMap
          triedLookup.get.unmintedIdentifiers should contain theSameElementsAs unmintedSourceIdentifiers
      }
    }

    it(
      "returns all of the sourceIdentifiers as notFound if it can't find any of the identifiers") {
      val sourceIdentifiers = (1 to 3).map(_ => createSourceIdentifier).toList

      withIdentifiersDao(existingEntries = Nil) {
        case (identifiersDao, _) =>
          val triedLookup = identifiersDao.lookupIds(
            sourceIdentifiers
          )
          triedLookup.get.existingIdentifiers shouldBe empty
          triedLookup.get.unmintedIdentifiers should contain theSameElementsAs sourceIdentifiers
      }
    }
  }

  describe("saveIdentifiers") {

    it("saves a single identifier in the database") {
      val identifier = createSQLIdentifier

      withIdentifiersDao() {
        case (identifiersDao, identifiersTable) =>
          implicit val session = NamedAutoSession('primary)

          identifiersDao.saveIdentifiers(List(identifier))
          val maybeIdentifier = withSQL {
            select
              .from(identifiersTable as identifiersTable.i)
              .where
              .eq(identifiersTable.i.SourceSystem, identifier.SourceSystem)
              .and
              .eq(
                identifiersTable.i.CanonicalId,
                identifier.CanonicalId.underlying)
          }.map(Identifier(identifiersTable.i)).single.apply()

          maybeIdentifier shouldBe defined
          maybeIdentifier.get shouldBe identifier
      }
    }

    it("saves multiple identifiers in the database") {
      val ids = (1 to 3).map { _ =>
        val sourceIdentifier = createSourceIdentifier
        (sourceIdentifier, Identifier(createCanonicalId, sourceIdentifier))
      }.toMap

      withIdentifiersDao() {
        case (identifiersDao, _) =>
          val result = for {
            insertResult <- identifiersDao.saveIdentifiers(ids.values.toList)
            lookupResult <- identifiersDao.lookupIds(ids.keys.toList)
          } yield (insertResult, lookupResult)

          result shouldBe a[Success[_]]
          val (insertResult, lookupResult) = result.get
          lookupResult.existingIdentifiers shouldBe ids
          insertResult.succeeded should contain theSameElementsAs lookupResult.existingIdentifiers.values
      }
    }

    it("fails saving the identifiers if the canonical id is already present") {
      val ids = (1 to 5).map { _ =>
        val sourceIdentifier = createSourceIdentifier
        (sourceIdentifier, Identifier(createCanonicalId, sourceIdentifier))
      }.toMap
      val identifiers = ids.values.toList
      val duplicatedIdentifier1 =
        createSQLIdentifierWith(canonicalId = identifiers.apply(1).CanonicalId)
      val duplicatedIdentifier2 =
        createSQLIdentifierWith(canonicalId = identifiers.apply(2).CanonicalId)

      withIdentifiersDao() {
        case (identifiersDao, _) =>
          val result = for {
            _ <- identifiersDao.saveIdentifiers(
              List(duplicatedIdentifier1, duplicatedIdentifier2))
            insertResult <- identifiersDao.saveIdentifiers(identifiers)
          } yield insertResult

          result shouldBe a[Failure[_]]
          val error = result.failed.get.asInstanceOf[IdentifiersDao.InsertError]
          error.e shouldBe a[BatchUpdateException]
          error.failed should contain theSameElementsAs identifiers
            .filter(i =>
              i.CanonicalId == duplicatedIdentifier1.CanonicalId || i.CanonicalId == duplicatedIdentifier2.CanonicalId)
          error.succeeded should contain theSameElementsAs identifiers
            .filterNot(i =>
              i.CanonicalId == duplicatedIdentifier1.CanonicalId || i.CanonicalId == duplicatedIdentifier2.CanonicalId)

          val lookupResult = identifiersDao.lookupIds(ids.keys.toList).get
          lookupResult.existingIdentifiers.values should contain theSameElementsAs error.succeeded
      }
    }

    it(
      "fails saving the identifiers if the same OntologyType, SourceSystem, and SourceId are already present") {
      val identifier = createSQLIdentifier
      val duplicateIdentifier = identifier.copy(CanonicalId = createCanonicalId)

      withIdentifiersDao() {
        case (identifiersDao, _) =>
          val result = for {
            _ <- identifiersDao.saveIdentifiers(List(identifier))
            insertResult <- identifiersDao.saveIdentifiers(
              List(duplicateIdentifier))
          } yield insertResult

          result shouldBe a[Failure[_]]
          val error = result.failed.get.asInstanceOf[IdentifiersDao.InsertError]
          error.e shouldBe a[BatchUpdateException]
          error.failed shouldBe List(duplicateIdentifier)
      }
    }

    it(
      "saves records with the same SourceSystem and SourceId but different OntologyType") {
      val sourceIdentifier1 = createSourceIdentifierWith(
        ontologyType = "Beep"
      )
      val sourceIdentifier2 = sourceIdentifier1.copy(
        ontologyType = "Boop"
      )

      val identifier1 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao() {
        case (identifiersDao, _) =>
          val result = for {
            insertResult <- identifiersDao.saveIdentifiers(
              List(identifier1, identifier2))
          } yield insertResult

          result shouldBe a[Success[_]]
      }
    }

    it(
      "saves records with different SourceId but the same OntologyType and SourceSystem") {
      val sourceIdentifier1 = createSourceIdentifier
      val sourceIdentifier2 =
        sourceIdentifier1.copy(value = randomAlphanumeric(10))

      val identifier1 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao() {
        case (identifiersDao, _) =>
          val result = for {
            insertResult <- identifiersDao.saveIdentifiers(
              List(identifier1, identifier2))
          } yield insertResult

          result shouldBe a[Success[_]]
      }
    }
  }

}
