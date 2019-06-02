package uk.ac.wellcome.platform.idminter.database

import org.scalatest.{EitherValues, FunSpec, Matchers}
import scalikejdbc._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.internal.IdentifierType
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.fixtures
import uk.ac.wellcome.platform.idminter.fixtures.IdMinterGenerators
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}
import uk.ac.wellcome.storage.{DaoWriteError, DoesNotExistError}

class SQLIdentifiersDaoTest
    extends FunSpec
    with fixtures.IdentifiersDatabase
    with Matchers
    with IdMinterGenerators
    with EitherValues {

  def withIdentifiersDao[R](
    testWith: TestWith[(SQLIdentifiersDao, IdentifiersTable), R]): R =
    withIdentifiersDatabase { identifiersTableConfig =>
      val identifiersTable = new IdentifiersTable(identifiersTableConfig)

      new TableProvisioner(rdsClientConfig)
        .provision(
          database = identifiersTableConfig.database,
          tableName = identifiersTableConfig.tableName
        )

      val identifiersDao = new SQLIdentifiersDao(DB.connect(), identifiersTable)

      eventuallyTableExists(identifiersTableConfig)

      testWith((identifiersDao, identifiersTable))
    }

  describe("get") {
    it("gets an Identifier if it finds a matching SourceSystem and SourceId") {
      val sourceIdentifier = createSourceIdentifier
      val identifier = createIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier) shouldBe a[Right[_, _]]

          identifiersDao.get(sourceIdentifier).right.value shouldBe identifier
      }
    }

    it("finds no identifier if the source identifier value is different") {
      val identifier = createIdentifier

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier) shouldBe a[Right[_, _]]

          val sourceIdentifier = toSourceIdentifier(identifier).copy(
            value = "not_an_existing_value"
          )

          identifiersDao.get(sourceIdentifier).left.value shouldBe a[DoesNotExistError]
      }
    }

    it("finds no identifier if the source identifier type is different") {
      val identifier = createIdentifierWith(
        sourceIdentifier = createSourceIdentifierWith(
          identifierType = IdentifierType("miro-image-number")
        )
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier) shouldBe a[Right[_, _]]

          val sourceIdentifier = toSourceIdentifier(identifier).copy(
            identifierType = IdentifierType("sierra-system-number")
          )

          identifiersDao.get(sourceIdentifier).left.value shouldBe a[DoesNotExistError]
      }
    }

    it("finds no identifier if the ontology type is different") {
      val identifier = createIdentifierWith(
        sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Agent"
        )
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier) shouldBe a[Right[_, _]]

          val sourceIdentifier = toSourceIdentifier(identifier).copy(
            ontologyType = "Item"
          )

          identifiersDao.get(sourceIdentifier).left.value shouldBe a[DoesNotExistError]
      }
    }
  }

  describe("put") {
    it("inserts the provided identifier into the database") {
      val identifier = createIdentifier

      withIdentifiersDao {
        case (identifiersDao, identifiersTable) =>
          implicit val session = AutoSession

          identifiersDao.put(identifier)
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
      val identifier = createIdentifier
      val duplicateIdentifier = createIdentifierWith(
        canonicalId = identifier.CanonicalId
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier) shouldBe a[Right[_, _]]

          val putResult = identifiersDao.put(duplicateIdentifier)
          putResult.left.value shouldBe a[DaoWriteError]
          putResult.left.value.e shouldBe a[IdMinterException]
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

      val identifier1 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier1) shouldBe a[Right[_, _]]
          identifiersDao.put(identifier2) shouldBe a[Right[_, _]]
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

      val identifier1 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier1) shouldBe a[Right[_, _]]
          identifiersDao.put(identifier2) shouldBe a[Right[_, _]]
      }
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

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.put(identifier1) shouldBe a[Right[_, _]]

          val triedSave = identifiersDao.put(identifier2)
          triedSave.left.value shouldBe a[DaoWriteError]
          triedSave.left.value.e shouldBe a[IdMinterException]
      }
    }
  }
}
