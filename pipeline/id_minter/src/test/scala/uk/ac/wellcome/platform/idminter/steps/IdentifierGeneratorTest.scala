package uk.ac.wellcome.platform.idminter.steps

import com.amazonaws.services.dynamodbv2.model.{AttributeDefinition, CreateTableRequest, GlobalSecondaryIndex, KeySchemaElement, KeyType, Projection, ProjectionType, ProvisionedThroughput}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.idminter.services.IdentifiersService
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.platform.idminter.utils.DynamoFormats._
import org.scanamo.auto._
import uk.ac.wellcome.platform.idminter.utils.DynamoIdentifierStore

import scala.util.{Failure, Success}

class IdentifierGeneratorTest
    extends FunSpec
    with Matchers
    with MockitoSugar
    with DynamoFixtures
    with IdentifiersGenerators {


  override def createTable(
                            table: DynamoFixtures.Table): DynamoFixtures.Table = {

    val sourceIdentifierIndex = new GlobalSecondaryIndex()
      .withIndexName("sourceIdentifierIndex")
      .withProvisionedThroughput(new ProvisionedThroughput()
        .withReadCapacityUnits(10.toLong)
        .withWriteCapacityUnits(1.toLong)
      )
      .withKeySchema(
        new KeySchemaElement()
          .withAttributeName("sourceIdentifier")
          .withKeyType(KeyType.HASH))
      .withProjection(
        new Projection().withProjectionType(ProjectionType.ALL)
      )

    createTableFromRequest(
      table = table,
      new CreateTableRequest()
        .withTableName(table.name)
        .withKeySchema(
          new KeySchemaElement()
            .withAttributeName("id")
            .withKeyType(KeyType.HASH))
        .withAttributeDefinitions(
          new AttributeDefinition()
            .withAttributeName("id")
            .withAttributeType("S")
        )
        .withAttributeDefinitions(
          new AttributeDefinition()
            .withAttributeName("sourceIdentifier")
            .withAttributeType("S")
        )
        .withProvisionedThroughput(new ProvisionedThroughput()
          .withReadCapacityUnits(1L)
          .withWriteCapacityUnits(1L))
        .withGlobalSecondaryIndexes(sourceIdentifierIndex)

    )
  }

  def withIdentifierGenerator[R](maybeIdentifiersDao: Option[IdentifiersService] = None)(
    testWith: TestWith[(IdentifierGenerator, DynamoIdentifierStore), R]): R = {
      withLocalDynamoDbTable { table =>

        val dynamoStore = new DynamoIdentifierStore(
          DynamoConfig(table.name, table.index)
        )

        val identifiersDao = maybeIdentifiersDao.getOrElse(
          new IdentifiersService(dynamoStore)
        )

        val identifierGenerator = new IdentifierGenerator(identifiersDao)

        testWith((identifierGenerator, dynamoStore))
      }
    }

  it("queries the database and return a matching canonical id") {
    val sourceIdentifier = createSourceIdentifier
    val canonicalId = createCanonicalId

    withIdentifierGenerator() { case (identifierGenerator, store) =>
        val identifier = Identifier(canonicalId, sourceIdentifier)

        store.put(identifier)

        val triedId = identifierGenerator.retrieveOrGenerateCanonicalId(
          sourceIdentifier
        )

        triedId shouldBe Success(canonicalId)
    }
  }

  it("generates and saves a new identifier") {
    val sourceIdentifier = createSourceIdentifier

    withIdentifierGenerator() { case (identifierGenerator, store) =>
        val triedId = identifierGenerator.retrieveOrGenerateCanonicalId(
          sourceIdentifier
        )

        triedId shouldBe a[Success[_]]

        val id = triedId.get
        id should not be empty

        val result = store.getBySourceIdentifier(sourceIdentifier)

        result.right.get shouldBe id
    }
  }

  it("returns a failure if it fails registering a new identifier") {
    val identifiersDao = mock[IdentifiersService]

    val sourceIdentifier = createSourceIdentifier

    val triedLookup = identifiersDao.lookupId(
      sourceIdentifier = sourceIdentifier
    )

    when(triedLookup).thenReturn(Success(None))

    val expectedException = new Exception("Noooo")

    val whenThing = identifiersDao.saveIdentifier(
      org.mockito.Matchers.eq(sourceIdentifier), any[Identifier]()
    )

    when(whenThing).thenReturn(Failure(expectedException))

    withIdentifierGenerator(Some(identifiersDao)) {
      case (identifierGenerator, _) =>
        val triedGeneratingId =
          identifierGenerator.retrieveOrGenerateCanonicalId(
            sourceIdentifier
          )

        triedGeneratingId shouldBe a[Failure[_]]
        triedGeneratingId.failed.get shouldBe expectedException
    }
  }

  it("preserves the ontologyType when generating a new identifier") {
    withIdentifierGenerator() { case (identifierGenerator, store) =>
        val sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Item"
        )

        val triedId = identifierGenerator.retrieveOrGenerateCanonicalId(
          sourceIdentifier
        )

        val id = triedId.get
        id should not be (empty)

        val result = store.getBySourceIdentifier(sourceIdentifier)

        result.right.get shouldBe id
    }
  }
}
