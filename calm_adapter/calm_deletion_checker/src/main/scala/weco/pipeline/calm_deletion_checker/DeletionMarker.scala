package weco.pipeline.calm_deletion_checker

import cats.implicits.toShow
import grizzled.slf4j.Logging
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.catalogue.source_model.CalmSourcePayload

import scala.util.{Failure, Success, Try}

class DeletionMarker(sourceTable: String)(implicit client: DynamoDbClient) extends Logging {
  import scala.language.higherKinds

  def apply(record: CalmSourcePayload): Try[CalmSourcePayload] =
    toTry(
      scanamo
        .exec(
          table
            .when(attributeExists("id"))
            .update(
              "id" === record.id,
              set("isDeleted", true)
            )
        )
        .map(_.toPayload)
    )(record)

  private def toTry(
    result: Either[ScanamoError, CalmSourcePayload]
  )(inputRecord: CalmSourcePayload) =
    result match {
      case Right(record) => Success(record)
      case Left(ConditionNotMet(e)) =>
        error(s"Record did not already exist: $inputRecord")
        Failure(e)
      case Left(readError: DynamoReadError) =>
        error(s"Read error while processing $inputRecord")
        Failure(new RuntimeException("Dynamo read error: " + readError.show))
    }

  private lazy val scanamo = Scanamo(client)
  private lazy val table = Table[CalmSourceDynamoRow](sourceTable)
}
