package uk.ac.wellcome.platform.transformer.miro.services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.scanamo.{Scanamo, Table}
import org.scanamo.auto._
import org.scanamo.syntax._
import uk.ac.wellcome.platform.transformer.miro.models.MiroVHSRecord
import uk.ac.wellcome.storage.{DoesNotExistError, Identified, StoreReadError}
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.store.Readable

import scala.util.{Failure, Success, Try}

class MiroDynamoVHSReader(config: DynamoConfig)(implicit client: AmazonDynamoDB)
    extends Readable[String, MiroVHSRecord] {

  private val scanamo = Scanamo(client)

  override def get(id: String): ReadEither = {
    val table = Table[MiroVHSRecord](config.tableName)

    Try(scanamo.exec(table.get('id -> id))) match {
      case Success(Some(Right(record))) => Right(Identified(id, record))

      case Success(Some(Left(err))) =>
        Left(
          StoreReadError(
            new Throwable(s"Error parsing the record from Scanamo: $err")))

      case Success(None) => Left(DoesNotExistError())

      case Failure(err) =>
        Left(StoreReadError(new Throwable(s"Error from Scanamo: $err")))
    }
  }
}
