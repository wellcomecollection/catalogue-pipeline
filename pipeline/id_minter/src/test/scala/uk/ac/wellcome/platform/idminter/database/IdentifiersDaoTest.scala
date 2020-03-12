package uk.ac.wellcome.platform.idminter.database

import java.sql.BatchUpdateException

import org.scalatest.{FunSpec, Matchers}
import scalikejdbc._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.fixtures
import uk.ac.wellcome.platform.idminter.fixtures.SqlIdentifiersGenerators
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}

import scala.util.{Failure, Success}

class IdentifiersDaoTest
    extends FunSpec
    with fixtures.IdentifiersDatabase
    with Matchers
    with IdentifiersGenerators with SqlIdentifiersGenerators{

  def withIdentifiersDao[R](
    testWith: TestWith[(IdentifiersDao, IdentifiersTable), R]): R =
    withIdentifiersDatabase { identifiersTableConfig =>
      val identifiersTable = new IdentifiersTable(identifiersTableConfig)

      new TableProvisioner(rdsClientConfig)
        .provision(
          database = identifiersTableConfig.database,
          tableName = identifiersTableConfig.tableName
        )

      val identifiersDao = new IdentifiersDao(DB.connect(), identifiersTable)

      eventuallyTableExists(identifiersTableConfig)

      testWith((identifiersDao, identifiersTable))
    }

  describe("lookupID") {
    it("gets an Identifier if it finds a matching SourceSystem and SourceId") {
      val sourceIdentifier = createSourceIdentifier
      val identifier = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(identifier) shouldBe Success(1)

          val triedLookup = identifiersDao.lookupId(
            sourceIdentifier = sourceIdentifier
          )

          triedLookup shouldBe Success(Some(identifier))
      }
    }

    it(
      "does not get an identifier if there is no matching SourceSystem and SourceId") {
      val identifier = createSQLIdentifier

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(identifier) shouldBe Success(1)

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
    it("inserts the provided identifier into the database") {
      val identifier = createSQLIdentifier

      withIdentifiersDao {
        case (identifiersDao, identifiersTable) =>
          implicit val session = AutoSession

          identifiersDao.saveIdentifier(identifier)
          val maybeIdentifier = withSQL {
            select
              .from(identifiersTable as identifiersTable.i)
              .where
              .eq(identifiersTable.i.SourceSystem, identifier.SourceSystem)
              .and
              .eq(identifiersTable.i.CanonicalId, identifier.CanonicalId)
          }.map(Identifier(identifiersTable.i)).single.apply()

          maybeIdentifier shouldBe defined
          maybeIdentifier.get shouldBe identifier
      }
    }

    it("fails to insert a record with a duplicate CanonicalId") {
      val identifier = createSQLIdentifier
      val duplicateIdentifier = createSQLIdentifierWith(
        canonicalId = identifier.CanonicalId
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(identifier) shouldBe Success(1)

          val triedSave = identifiersDao.saveIdentifier(duplicateIdentifier)

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

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(identifier1) shouldBe Success(1)
          identifiersDao.saveIdentifier(identifier2) shouldBe Success(1)
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

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(identifier1) shouldBe Success(1)
          identifiersDao.saveIdentifier(identifier2) shouldBe Success(1)
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

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(identifier1) shouldBe Success(1)

          val triedSave = identifiersDao.saveIdentifier(identifier2)

          triedSave shouldBe a[Failure[_]]
          triedSave.failed.get shouldBe a[IdMinterException]
      }
    }
  }

  describe("lookupIds") {

    it("retrieves a single id from the identifiers database") {
      val sourceIdentifier = createSourceIdentifier
      val identifier = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          val triedLookup = for{
            _ <-identifiersDao.saveIdentifiers(List(identifier))
            lookupResult <-identifiersDao.lookupIds(List(sourceIdentifier))
          } yield(lookupResult)

          triedLookup shouldBe a [Right[_,_]]
          triedLookup.right.get.found shouldBe Map(sourceIdentifier->identifier)
          triedLookup.right.get.notFound shouldBe empty
      }
    }

    it("retrieves multiple ids from the identifiers database") {
      val identifiersMap = (1 to 3).map {_ =>
        val sourceIdentifier = createSourceIdentifier
        val identifier = createSQLIdentifierWith(sourceIdentifier = sourceIdentifier)
        (sourceIdentifier, identifier)
      }.toMap

      withIdentifiersDao {
        case (identifiersDao, _) =>
          val triedLookup = for{
            _ <-identifiersDao.saveIdentifiers(identifiersMap.values.toList)
            lookupResult <-identifiersDao.lookupIds(identifiersMap.keys.toList)
          } yield(lookupResult)

          triedLookup shouldBe a [Right[_,_]]
        triedLookup.right.get.found shouldBe identifiersMap
        triedLookup.right.get.notFound shouldBe empty
      }
    }

    it("matches correctly the ids to the sourceIdentifier when the sourceIdentifiers have overlapping values") {
      val sourceIdentifier = createSourceIdentifier
      val overlappingSourceIdentifier = sourceIdentifier.copy(ontologyType = "Item")
      val sourceIdentifiers = List(sourceIdentifier, overlappingSourceIdentifier)
      val identifiers = sourceIdentifiers.map (sourceIdentifier =>createSQLIdentifierWith(sourceIdentifier = sourceIdentifier))

      withIdentifiersDao {
        case (identifiersDao, _) =>
          val triedLookup = for{
            _ <-identifiersDao.saveIdentifiers(identifiers)
            lookupResult <-identifiersDao.lookupIds(sourceIdentifiers)
          } yield(lookupResult)

          triedLookup shouldBe a [Right[_,_]]
          triedLookup.right.get.found shouldBe sourceIdentifiers.zip(identifiers).toMap
          triedLookup.right.get.notFound shouldBe empty
      }
    }

    it("retrieves multiple ids and returns the ones it doesn't have in notFound") {
      val identifiersMap = (1 to 3).map {_ =>
        val sourceIdentifier = createSourceIdentifier
        val identifier = createSQLIdentifierWith(sourceIdentifier = sourceIdentifier)
        (sourceIdentifier, identifier)
      }.toMap
      val nonExistingSourceIdentifiers = (1 to 2).map (_ => createSourceIdentifier).toList

      withIdentifiersDao {
        case (identifiersDao, _) =>
          val triedLookup = for{
            _ <-identifiersDao.saveIdentifiers(identifiersMap.values.toList)
            lookupResult <-identifiersDao.lookupIds(identifiersMap.keys.toList ++ nonExistingSourceIdentifiers)
          } yield(lookupResult)

          triedLookup shouldBe a [Right[_,_]]

          triedLookup.right.get.found shouldBe identifiersMap
          triedLookup.right.get.notFound should contain theSameElementsAs (nonExistingSourceIdentifiers)
      }
    }

  it("returns all of the sourceIdentifiers as notFound if it can't find any of the identifiers") {
      val sourceIdentifiers = (1 to 3).map (_ => createSourceIdentifier).toList

      withIdentifiersDao {
        case (identifiersDao, _) =>


          val triedLookup = identifiersDao.lookupIds(
            sourceIdentifiers
          )
          triedLookup.right.get.found shouldBe empty
          triedLookup.right.get.notFound should contain theSameElementsAs(sourceIdentifiers)
      }
    }
  }

  describe("saveIdentifiers"){

    it("saves a single identifier in the database"){
      val identifier = createSQLIdentifier

      withIdentifiersDao {
        case (identifiersDao, identifiersTable) =>
          implicit val session = AutoSession

          identifiersDao.saveIdentifiers(List(identifier))
          val maybeIdentifier = withSQL {
            select
              .from(identifiersTable as identifiersTable.i)
              .where
              .eq(identifiersTable.i.SourceSystem, identifier.SourceSystem)
              .and
              .eq(identifiersTable.i.CanonicalId, identifier.CanonicalId)
          }.map(Identifier(identifiersTable.i)).single.apply()

          maybeIdentifier shouldBe defined
          maybeIdentifier.get shouldBe identifier
      }
    }

    it("saves multiple identifiers in the database"){
      val ids = (1 to 3).map{_ =>
        val sourceIdentifier = createSourceIdentifier
        (sourceIdentifier, Identifier(createCanonicalId, sourceIdentifier))
      }.toMap

      withIdentifiersDao {
        case (identifiersDao, _) =>

          val result = for {
           insertResult <-  identifiersDao.saveIdentifiers(ids.values.toList)
           lookupResult <- identifiersDao.lookupIds(ids.keys.toList)
          } yield (insertResult,lookupResult)

          result shouldBe a[Right[_,_]]
          val (insertResult,lookupResult) = result.right.get
          lookupResult.found shouldBe ids
          insertResult.succeeded should contain theSameElementsAs(lookupResult.found.values)
      }
    }

    it("fails saving the identifiers if the canonical id is already present"){
      val ids = (1 to 5).map{_ =>
        val sourceIdentifier = createSourceIdentifier
        (sourceIdentifier, Identifier(createCanonicalId, sourceIdentifier))
      }.toMap
      val identifiers = ids.values.toList
      val duplicatedIdentifier1 = createSQLIdentifierWith(canonicalId = identifiers.apply(1).CanonicalId)
      val duplicatedIdentifier2 = createSQLIdentifierWith(canonicalId = identifiers.apply(3).CanonicalId)

      withIdentifiersDao {
        case (identifiersDao, _) =>

          val result = for {
            _<- identifiersDao.saveIdentifiers(List(duplicatedIdentifier1, duplicatedIdentifier2))
           insertResult <-  identifiersDao.saveIdentifiers(identifiers)
          } yield (insertResult)

          result shouldBe a[Left[_,_]]
          result.left.get.exception shouldBe a[BatchUpdateException]
          result.left.get.failed should contain theSameElementsAs(identifiers.filter(i => i.CanonicalId == duplicatedIdentifier1.CanonicalId || i.CanonicalId == duplicatedIdentifier2.CanonicalId))
          result.left.get.succeeded should contain theSameElementsAs(identifiers.filterNot(i => i.CanonicalId == duplicatedIdentifier1.CanonicalId || i.CanonicalId == duplicatedIdentifier2.CanonicalId))

          val lookupResult = identifiersDao.lookupIds(ids.keys.toList).right.get
          lookupResult.found.values should contain theSameElementsAs(result.left.get.succeeded)
      }
    }

    it("fails saving the identifiers if the same OntologyType,SourceName, and SourceId are already present"){
      val identifier = createSQLIdentifier
      val duplicateIdentifier = identifier.copy(CanonicalId = createCanonicalId)

      withIdentifiersDao {
        case (identifiersDao, _) =>

          val result = for {
            _<- identifiersDao.saveIdentifiers(List(identifier))
           insertResult <-  identifiersDao.saveIdentifiers(List(duplicateIdentifier))
          } yield (insertResult)

          result shouldBe a[Left[_,_]]
          result.left.get.exception shouldBe a[BatchUpdateException]
          result.left.get.failed shouldBe List(duplicateIdentifier)
      }
    }

    it(
      "saves records with the same SourceSystem and SourceId but different OntologyType") {
      val sourceIdentifier1 = createSourceIdentifier
      val sourceIdentifier2 = sourceIdentifier1.copy(ontologyType = "Foo")

      val identifier1 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          val result = for {
            insertResult<- identifiersDao.saveIdentifiers(List(identifier1, identifier2))
          } yield (insertResult)

          result shouldBe a[Right[_,_]]
      }
    }

    it(
      "saves records with different SourceId but the same OntologyType and SourceSystem") {
      val sourceIdentifier1 = createSourceIdentifier
      val sourceIdentifier2 = sourceIdentifier1.copy(value = randomAlphanumeric(10))

      val identifier1 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createSQLIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          val result = for {
            insertResult<- identifiersDao.saveIdentifiers(List(identifier1, identifier2))
          } yield (insertResult)

          result shouldBe a[Right[_,_]]
      }
    }
  }

}
