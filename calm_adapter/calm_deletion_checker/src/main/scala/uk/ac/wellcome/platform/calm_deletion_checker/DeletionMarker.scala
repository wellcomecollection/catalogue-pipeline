package uk.ac.wellcome.platform.calm_deletion_checker

import cats.implicits.toShow
import org.scanamo.syntax._
import org.scanamo.{
  ConditionNotMet,
  DynamoReadError,
  Scanamo,
  ScanamoError,
  Table
}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.catalogue.source_model.CalmSourcePayload

import scala.util.{Failure, Success, Try}

class DeletionMarker(sourceTable: Table[CalmSourcePayload])(
  implicit client: DynamoDbClient) {

  def apply(record: CalmSourcePayload): Try[CalmSourcePayload] =
    toTry(
      scanamo.exec(
        sourceTable
          .when(attributeNotExists("isDeleted") or "isDeleted" === false)
          .update(
            "id" === record.id and "version" === record.version,
            set("isDeleted", true)
          )
      )
    )

  private def toTry(result: Either[ScanamoError, CalmSourcePayload]) =
    result match {
      case Right(record)                    => Success(record)
      case Left(ConditionNotMet(exception)) => Failure(exception)
      case Left(error: DynamoReadError) =>
        Failure(new RuntimeException("Dynamo read error: " + error.show))
    }

  private lazy val scanamo = Scanamo(client)
}
