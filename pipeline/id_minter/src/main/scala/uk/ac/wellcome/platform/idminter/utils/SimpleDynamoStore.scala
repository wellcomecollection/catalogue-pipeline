package uk.ac.wellcome.platform.idminter.utils

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.scanamo.{DynamoFormat, Scanamo, Table}
import org.scanamo.query.{ConditionalOperation, Query}
import uk.ac.wellcome.storage.{DoesNotExistError, Identified, MultipleRecordsError, StoreReadError, StoreWriteError}
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.store.{Readable, Store, Writable}

import scala.util.{Failure, Success, Try}

trait SimpleDynamoWritable[HashKey, T]
  extends Writable[HashKey, T] {

  import org.scanamo.syntax._

  protected val client: AmazonDynamoDB
  protected val table: Table[Identified[HashKey, T]]

  protected def parseEntry(entry: Identified[HashKey, T]): T =
    entry.identifiedT

  private def createEntry(id: HashKey, t: T): Identified[HashKey, T] =
    Identified(id, t)

  private def tableGiven(id: HashKey): ConditionalOperation[Identified[HashKey, T], _] =
    table.given(not(attributeExists('id)))

  override def put(id: HashKey)(t: T): WriteEither = {
    val entry = createEntry(id, t)

    val ops = tableGiven(id).put(entry)

    Try(Scanamo(client).exec(ops)) match {
      case Success(Right(_))  => Right(Identified(id, parseEntry(entry)))
      case Success(Left(err)) => Left(StoreWriteError(err))
      case Failure(err)       => Left(StoreWriteError(err))
    }
  }
}

trait SimpleDynamoReadable[HashKey, T]
  extends Readable[HashKey, T] {

  import org.scanamo.syntax._

  implicit protected val format: DynamoFormat[Identified[HashKey, T]]

  protected val client: AmazonDynamoDB
  protected val table: Table[Identified[HashKey, T]]

  implicit protected val formatHashKey: DynamoFormat[HashKey]

  protected def createKeyExpression(id: HashKey): Query[_] =
    'id -> id

  def get(id: HashKey): ReadEither = {
    val ops = table.query(createKeyExpression(id))

    Try(Scanamo(client).exec(ops)) match {
      case Success(List(Right(entry))) => Right(entry)
      case Success(List(Left(err))) =>
        val daoReadError = new Error(s"DynamoReadError: ${err.toString}")
        Left(StoreReadError(daoReadError))

      case Success(list) if list.length > 1 =>
        Left(
          MultipleRecordsError()
        )
      case Success(Nil) => Left(DoesNotExistError())
      case Failure(err) => Left(StoreReadError(err))
    }
  }
}

class SimpleDynamoStore[HashKey, T](
                                     val config: DynamoConfig
                                   )(
                                     implicit
                                     val client: AmazonDynamoDB,
                                     val formatHashKey: DynamoFormat[HashKey],
                                     val format: DynamoFormat[Identified[HashKey, T]]
                                   ) extends Store[HashKey, T]
  with SimpleDynamoReadable[HashKey, T]
  with SimpleDynamoWritable[HashKey, T] {

  override protected val table: Table[Identified[HashKey, T]] =
    Table[Identified[HashKey, T]](config.tableName)
}
