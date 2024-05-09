package weco.pipeline.reindex_worker.dynamo

import org.scanamo.generic.auto._
import org.scanamo.{Scanamo, Table => ScanamoTable}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import weco.pipeline.reindex_worker.fixtures.ReindexDynamoFixtures
import weco.storage.fixtures.DynamoFixtures.Table

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds

class MultiItemGetterTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with ReindexDynamoFixtures {
  val multiItemGetter = new MultiItemGetter()

  it("finds a single specified record") {
    withLocalDynamoDbTable {
      table =>
        val records = createRecords(table, count = 5)
        val specifiedRecord = records.head

        val future = multiItemGetter.get[NamedRecord](
          ids = Seq(specifiedRecord.id)
        )(table.name)

        whenReady(future) {
          _ shouldBe Seq(specifiedRecord)
        }
    }
  }

  it("finds a list of specified records") {
    withLocalDynamoDbTable {
      table =>
        val records = createRecords(table, count = 5)
        val specifiedRecords = records.slice(1, 3)

        val future = multiItemGetter.get[NamedRecord](specifiedRecords.map {
          _.id
        }.toList)(table.name)

        whenReady(future) {
          _ should contain theSameElementsAs specifiedRecords
        }
    }
  }

  it("handles being asked for a non-existent record") {
    withLocalDynamoDbTable {
      table =>
        createRecords(table, count = 5)

        val future =
          multiItemGetter.get[NamedRecord](List("bananas"))(table.name)

        whenReady(future) {
          _ shouldBe empty
        }
    }
  }

  it("handles being asked for a mix of valid and non-existent records") {
    withLocalDynamoDbTable {
      table =>
        val records = createRecords(table, count = 5)
        val specifiedRecordIds =
          List(records.head.id, "durian", records(1).id, "jackfruit")

        val future =
          multiItemGetter.get[NamedRecord](specifiedRecordIds)(table.name)

        whenReady(future) {
          _ should contain theSameElementsAs Seq(records(0), records(1))
        }
    }
  }

  it("reads from the right partition key") {
    case class Shape(sides: String, name: String)

    val createShapeTable: Table => Table = (table: Table) =>
      createTableWithHashKey(
        table,
        keyName = "sides",
        keyType = ScalarAttributeType.S
      )

    val shapes = Seq(
      Shape(sides = "3", name = "triangle"),
      Shape(sides = "4", name = "quadrilateral"),
      Shape(sides = "5", name = "pentagon")
    )

    withSpecifiedTable(createShapeTable) {
      table =>
        shapes.foreach {
          s =>
            val scanamoTable = ScanamoTable[Shape](table.name)
            Scanamo(dynamoClient).exec(scanamoTable.put(s))
        }

        val future = multiItemGetter.get[Shape](
          partitionKey = "sides",
          ids = Seq("3", "4")
        )(table.name)

        whenReady(future) {
          _ should contain theSameElementsAs Seq(shapes(0), shapes(1))
        }
    }
  }

  it("fails if the data is in the wrong format") {
    case class NumberedRecord(id: Int, text: String)

    withLocalDynamoDbTable {
      table =>
        val records = createRecords(table, count = 5)
        val ids = records.map { _.id }

        val future = multiItemGetter.get[NumberedRecord](ids = ids)(table.name)

        whenReady(future.failed) {
          err =>
            err shouldBe a[Throwable]
            err.getMessage should startWith("Errors parsing Scanamo result")
        }
    }
  }
}
