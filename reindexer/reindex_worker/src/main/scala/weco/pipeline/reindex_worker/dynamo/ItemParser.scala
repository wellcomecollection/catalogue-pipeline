package weco.pipeline.reindex_worker.dynamo

import java.util

import org.scanamo.{DynamoFormat, DynamoObject, ScanamoFree}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.concurrent.{ExecutionContext, Future}

// Includes utilities for parsing a sequence of DynamoDB Items from
// the Java API as typed objects in Scala.
trait ItemParser {
  implicit val ec: ExecutionContext

  protected def parseItems[T](
    items: Seq[util.Map[String, AttributeValue]]
  )(implicit format: DynamoFormat[T]): Future[Seq[T]] =
    Future {
      val results = items.map { it =>
        val dynamoObject: DynamoObject = DynamoObject(it)

        ScanamoFree.read[T](dynamoObject)
      }

      val successes = results.collect { case Right(t) => t }
      val failures = results.collect { case Left(err) => err }

      if (failures.isEmpty) {
        successes
      } else {
        throw new RuntimeException(s"Errors parsing Scanamo result: $failures")
      }
    }
}
