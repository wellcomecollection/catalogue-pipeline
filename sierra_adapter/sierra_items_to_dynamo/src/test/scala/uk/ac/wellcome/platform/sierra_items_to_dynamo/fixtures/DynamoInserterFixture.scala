package uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.DynamoInserter
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}

trait DynamoInserterFixture {
  def createStore(data: Map[Version[String, Int],SierraItemRecord]= Map.empty) = {
    new MemoryVersionedStore(new MemoryStore(data) with MemoryMaxima[String, SierraItemRecord])
  }

  def withDynamoInserter[R](store: VersionedStore[String, Int, SierraItemRecord])(
    testWith: TestWith[DynamoInserter, R]): R ={
      val dynamoInserter = new DynamoInserter(
        versionedHybridStore = store
      )
      testWith(dynamoInserter)
    }
}
