package weco.pipeline.calm_deletion_checker.fixtures

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import org.scanamo.{Table => ScanamoTable}
import org.scanamo.syntax._
import weco.fixtures.TestWith
import weco.storage.Version
import weco.storage.fixtures.DynamoFixtures
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.maxima.Maxima
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.dynamo.DynamoHashStore
import weco.storage.store.memory.MemoryTypedStore
import weco.storage.store.{
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import weco.storage.streaming.Codec
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.store.SourceVHS
import weco.catalogue.source_model.Implicits._
import weco.pipeline.calm_deletion_checker.CalmSourceDynamoRow

import scala.language.higherKinds

trait DynamoCalmVHSFixture
    extends DynamoFixtures
    with S3ObjectLocationGenerators
    with Matchers
    with EitherValues {
  def createStore[T: Codec](
    table: DynamoFixtures.Table
  ): VersionedHybridStore[String, Int, S3ObjectLocation, T] = {
    val dynamoConfig = createDynamoConfigWith(table)
    val hybridStore = {
      new HybridStoreWithMaxima[String, Int, S3ObjectLocation, T] {
        implicit override val indexedStore
          : Store[Version[String, Int], S3ObjectLocation]
            with Maxima[String, Version[String, Int], S3ObjectLocation] =
          new DynamoHashStore[String, Int, S3ObjectLocation](dynamoConfig)

        override implicit val typedStore: TypedStore[S3ObjectLocation, T] =
          MemoryTypedStore[S3ObjectLocation, T]()

        override protected def createTypeStoreId(
          id: Version[String, Int]
        ): S3ObjectLocation = createS3ObjectLocation
      }
    }
    new VersionedHybridStore(hybridStore)
  }

  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table =
    createTableWithHashKey(table)

  def withDynamoSourceVHS[R](entries: Seq[CalmRecord])(
    testWith: TestWith[
      (
        SourceVHS[CalmRecord],
        DynamoFixtures.Table,
        () => Seq[CalmSourceDynamoRow]
      ),
      R
    ]
  ): R = withLocalDynamoDbTable {
    table =>
      val sourceVhs = new SourceVHS[CalmRecord](createStore[CalmRecord](table))
      val ids = entries.map {
        record =>
          val result = sourceVhs.putLatest(record.id)(record)
          result.value.id
      }

      def getRows: Seq[CalmSourceDynamoRow] =
        ids.flatMap {
          case Version(id, _) =>
            scanamo
              .exec {
                ScanamoTable[CalmSourceDynamoRow](table.name)
                  .get("id" === id)
              }
              .map(_.value)
        }

      testWith((sourceVhs, table, getRows _))
  }
}
