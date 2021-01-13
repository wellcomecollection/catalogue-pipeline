package uk.ac.wellcome.platform.matcher.storage.dynamo

import com.amazonaws.services.dynamodbv2.model.{
  ResourceNotFoundException,
  ScalarAttributeType
}
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.auto._
import org.scanamo.syntax._
import org.scanamo.{Table => ScanamoTable}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class DynamoBatchWriterTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with DynamoFixtures
    with ScalaFutures {
  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table =
    createTableWithHashKey(
      table,
      keyName = "sides",
      keyType = ScalarAttributeType.N)

  case class Shape(sides: Int, description: String)

  it("writes some items to DynamoDB") {
    withLocalDynamoDbTable { table =>
      val writer = new DynamoBatchWriter[Shape](createDynamoConfigWith(table))

      val shapes = Seq(
        Shape(sides = 3, description = "yellow triangle"),
        Shape(sides = 4, description = "red square"),
        Shape(sides = 5, description = "blue pentagon")
      )

      whenReady(writer.batchWrite(shapes)) { _ =>
        shapes.foreach { s =>
          scanamo
            .exec(ScanamoTable[Shape](table.name).get('sides -> s.sides))
            .get
            .value shouldBe s
        }
      }
    }
  }

  it("writes lots of items (>25) to DynamoDB") {
    withLocalDynamoDbTable { table =>
      val writer = new DynamoBatchWriter[Shape](createDynamoConfigWith(table))

      val shapes = (1 to 100).map { sides =>
        Shape(sides = sides, description = "a mysterious shape")
      }

      whenReady(writer.batchWrite(shapes)) { _ =>
        shapes.foreach { s =>
          scanamo
            .exec(ScanamoTable[Shape](table.name).get('sides -> s.sides))
            .get
            .value shouldBe s
        }
      }
    }
  }

  it("fails if we try to write to a non-existent table") {
    val writer =
      new DynamoBatchWriter[Shape](createDynamoConfigWith(nonExistentTable))

    val shapes = (1 to 100).map { sides =>
      Shape(sides = sides, description = "an invisible shape")
    }

    whenReady(writer.batchWrite(shapes).failed) {
      _ shouldBe a[ResourceNotFoundException]
    }
  }
}
