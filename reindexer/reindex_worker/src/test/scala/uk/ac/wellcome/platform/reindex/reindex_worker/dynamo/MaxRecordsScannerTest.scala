package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.gu.scanamo.Scanamo
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.fixtures.LocalDynamoDbVersioned

class MaxRecordsScannerTest
    extends FunSpec
    with Matchers
    with DynamoFixtures
    with LocalDynamoDbVersioned {

  it("reads a table with a single record") {
    withLocalDynamoDbTable { table =>
      withMaxRecordsScanner { maxResultScanner =>
        val record = VersionedRecord(id = "123", data = "hello world", version = 1)
        Scanamo.put(dynamoDbClient)(table.name)(record)

        val result = maxResultScanner.scan(maxRecords = 1)(table.name)

        parseRecords[VersionedRecord](result) shouldBe Seq(record)
      }
    }
  }

  it("handles being asked for more records than are in the table") {
    withLocalDynamoDbTable { table =>
      withMaxRecordsScanner { maxResultScanner =>
        val records = (1 to 5).map { id =>
          VersionedRecord(id = id.toString, data = "Hello world", version = 1)
        }

        records.map { record =>
          Scanamo.put(dynamoDbClient)(table.name)(record)
        }

        val result = maxResultScanner.scan(maxRecords = 10)(table.name)

        parseRecords[VersionedRecord](result) should contain theSameElementsAs records
      }
    }
  }
}
