package uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.DynamoInserter
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.store.VersionedStore

trait DynamoInserterFixture {

  def withDynamoInserter[R](store: VersionedStore[String, Int, SierraItemRecord])(
    testWith: TestWith[DynamoInserter, R]): R ={
      val dynamoInserter = new DynamoInserter(
        versionedHybridStore = store
      )
      testWith(dynamoInserter)
    }
}
