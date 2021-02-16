package uk.ac.wellcome.platform.calm_deletion_checker.fixtures

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import org.scanamo.{Table => ScanamoTable}
import org.scanamo.syntax._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.calm_deletion_checker.CalmSourceDynamoRow
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.dynamo.DynamoHashRangeStore
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import uk.ac.wellcome.storage.store.{
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.streaming.Codec
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.store.SourceVHS

import scala.language.higherKinds

trait DynamoCalmVHSFixture
    extends DynamoFixtures
    with S3ObjectLocationGenerators
    with Matchers
    with EitherValues {
  def createStore[T: Codec](table: DynamoFixtures.Table)
    : VersionedHybridStore[String, Int, S3ObjectLocation, T] = {
    val dynamoConfig = DynamoConfig(table.name, table.index)
    val hybridStore = {
      new HybridStoreWithMaxima[String, Int, S3ObjectLocation, T] {
        implicit override val indexedStore
          : Store[Version[String, Int], S3ObjectLocation] with Maxima[
            String,
            Version[String, Int],
            S3ObjectLocation] =
          new DynamoHashRangeStore[String, Int, S3ObjectLocation](dynamoConfig)

        override implicit val typedStore: TypedStore[S3ObjectLocation, T] =
          MemoryTypedStore[S3ObjectLocation, T]()

        override protected def createTypeStoreId(
          id: Version[String, Int]): S3ObjectLocation = createS3ObjectLocation
      }
    }
    new VersionedHybridStore(hybridStore)
  }

  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table =
    createTableWithHashRangeKey(table)

  def withDynamoSourceVHS[R](entries: Seq[CalmRecord])(
    testWith: TestWith[(SourceVHS[CalmRecord],
                        DynamoFixtures.Table,
                        () => Seq[CalmSourceDynamoRow]),
                       R]
  ): R = withLocalDynamoDbTable { table =>
    val sourceVhs = new SourceVHS[CalmRecord](createStore[CalmRecord](table))
    val ids = entries.map { record =>
      val result = sourceVhs.putLatest(record.id)(record)
      result.value.id
    }

    def getRows: Seq[CalmSourceDynamoRow] =
      ids.flatMap {
        case Version(id, version) =>
          scanamo
            .exec {
              ScanamoTable[CalmSourceDynamoRow](table.name)
                .get("id" === id and "version" === version)
            }
            .map(_.value)
      }

    testWith((sourceVhs, table, getRows _))
  }
}
