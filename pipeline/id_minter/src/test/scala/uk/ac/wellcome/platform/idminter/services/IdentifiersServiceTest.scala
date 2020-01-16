package uk.ac.wellcome.platform.idminter.services

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.utils.SimpleDynamoStore
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.fixtures.DynamoFixtures

import scala.util.{Failure, Success}
import uk.ac.wellcome.platform.idminter.utils.DynamoFormats._
import org.scanamo.auto._

class IdentifiersServiceTest
    extends FunSpec
    with Matchers
    with DynamoFixtures
    with IdentifiersGenerators {

  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table = {
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S
    )
  }

  type StoreType = SimpleDynamoStore[SourceIdentifier, Identifier]

  def withIdentifiersDao[R](
    testWith: TestWith[(IdentifiersService[StoreType], StoreType), R]): R = {
    withLocalDynamoDbTable { table =>
      // TODO: Deal with slashes in identifier ontology type & id type must not have
      val dynamoStore = new SimpleDynamoStore[SourceIdentifier, Identifier](
        DynamoConfig(table.name, table.index)
      )

      val identifiersDao = new IdentifiersService(dynamoStore)

      testWith((identifiersDao, dynamoStore))
    }
  }

  describe("lookupID") {
    it("gets an Identifier if it finds a matching SourceSystem and SourceId") {
      val sourceIdentifier = createSourceIdentifier
      val identifier = createIdentifierWith(
        sourceIdentifier = sourceIdentifier
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(
            sourceIdentifier,
            identifier
          ) shouldBe a[Success[_]]

          val triedLookup = identifiersDao.lookupId(
            sourceIdentifier = sourceIdentifier
          )

          triedLookup shouldBe Success(Some(identifier))
      }
    }

    it(
      "does not get an identifier if there is no matching SourceSystem and SourceId") {
      val (sourceIdentifier, identifier) = createIdentifier

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(sourceIdentifier, identifier) shouldBe a[
            Success[_]]

          val unknownSourceIdentifier = createSourceIdentifierWith(
            ontologyType = identifier.ontologyType,
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
      val (sourceIdentifier, identifier) = createIdentifier

      withIdentifiersDao {
        case (identifiersDao, memoryStore) =>
          identifiersDao.saveIdentifier(sourceIdentifier, identifier)

          val result = memoryStore.get(sourceIdentifier)

          result.right.get.identifiedT shouldBe identifier
      }
    }

    it("fails to insert a record with a duplicate CanonicalId") {
      val (sourceIdentifier, identifier) = createIdentifier

      val newSourceIdentifier = createSourceIdentifier
      val duplicateIdentifier = createIdentifierWith(
        sourceIdentifier = newSourceIdentifier,
        canonicalId = identifier.canonicalId
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(
            sourceIdentifier,
            identifier
          ) shouldBe a[Success[_]]

          val triedSave = identifiersDao.saveIdentifier(
            newSourceIdentifier,
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

      val identifier1 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier1
      )

      val identifier2 = createIdentifierWith(
        sourceIdentifier = sourceIdentifier2
      )

      withIdentifiersDao {
        case (identifiersDao, _) =>
          identifiersDao.saveIdentifier(
            sourceIdentifier1,
            identifier1
          ) shouldBe a[Success[_]]

          identifiersDao.saveIdentifier(
            sourceIdentifier2,
            identifier2
          ) shouldBe a[Success[_]]
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
          identifiersDao.saveIdentifier(
            sourceIdentifier1,
            identifier1
          ) shouldBe a[Success[_]]

          identifiersDao.saveIdentifier(
            sourceIdentifier2,
            identifier2
          ) shouldBe a[Success[_]]
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
          identifiersDao.saveIdentifier(
            sourceIdentifier,
            identifier1
          ) shouldBe a[Success[_]]

          val triedSave = identifiersDao.saveIdentifier(
            sourceIdentifier,
            identifier2
          )

          triedSave shouldBe a[Failure[_]]
          triedSave.failed.get shouldBe a[IdMinterException]
      }
    }
  }

  def createIdentifierWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier
  ): Identifier =
    Identifier(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier
    )

  def createIdentifier: (SourceIdentifier, Identifier) = {

    val sourceIdentifier = createSourceIdentifier
    val sqlIdentifier = createIdentifierWith(
      sourceIdentifier = sourceIdentifier
    )

    (sourceIdentifier, sqlIdentifier)
  }
}
