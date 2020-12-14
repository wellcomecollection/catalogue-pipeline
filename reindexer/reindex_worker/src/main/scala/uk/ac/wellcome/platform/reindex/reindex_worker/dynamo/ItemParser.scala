package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.document.{Item, ItemUtils}
import org.scanamo.{DynamoFormat, DynamoObject, ScanamoFree}

import scala.concurrent.{ExecutionContext, Future}

// Includes utilities for parsing a sequence of DynamoDB Items from
// the Java API as typed objects in Scala.
trait ItemParser {
  implicit val ec: ExecutionContext

  protected def parseItems[T](items: Seq[Item])(
    implicit format: DynamoFormat[T]): Future[Seq[T]] =
    Future {
      val results = items.map { it =>
        val dynamoObject: DynamoObject =
          DynamoObject(ItemUtils.toAttributeValues(it))

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
