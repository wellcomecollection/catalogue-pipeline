package uk.ac.wellcome.platform.recorder

import java.util.UUID
import scala.util.{Failure, Success, Try}
import org.scanamo.DynamoFormat
import com.typesafe.config.Config

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.dynamo.DynamoHashEntry
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStoreWithMaxima,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.{
  Identified,
  ObjectLocation,
  ObjectLocationPrefix,
  Version
}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.storage.streaming.Codec._

case class EmptyMetadata()

trait GetLocation {
  def getLocation(key: Version[String, Int]): Try[ObjectLocation]
}

class RecorderVhs(hybridStore: RecorderHybridStore)
    extends VersionedHybridStore[
      String,
      Int,
      ObjectLocation,
      TransformedBaseWork,
      EmptyMetadata
    ](hybridStore)
    with GetLocation {

  def getLocation(key: Version[String, Int]): Try[ObjectLocation] =
    hybridStore.indexedStore.get(key) match {
      case Right(Identified(_, entry)) => Success(entry.typedStoreId)
      case Left(error)                 => Failure(error.e)
    }
}

class RecorderHybridStore(
  prefix: ObjectLocationPrefix,
  dynamoIndexStore: DynamoHashStore[
    String,
    Int,
    HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]],
  s3TypedStore: S3TypedStore[TransformedBaseWork]
) extends HybridStoreWithMaxima[
      String,
      Int,
      ObjectLocation,
      TransformedBaseWork,
      EmptyMetadata] {

  override val indexedStore = dynamoIndexStore;
  override val typedStore = s3TypedStore;

  override protected def createTypeStoreId(
    id: Version[String, Int]): ObjectLocation =
    prefix.asLocation(
      id.id.toString,
      id.version.toString,
      UUID.randomUUID().toString)
}

object RecorderVhs {

  type IndexEntry = HybridIndexedStoreEntry[ObjectLocation, EmptyMetadata]

  type HashEntry = DynamoHashEntry[String, Int, IndexEntry]

  def build(config: Config): RecorderVhs = {
    // TODO: from where do we get the correct values for this?
    val objectLocationPrefix = ObjectLocationPrefix("namespace", "path")
    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = "namespace")

    // For some reason these dont get derived with scanamo auto derivation
    implicit def indexEntryFormat: DynamoFormat[IndexEntry] =
      DynamoFormat[IndexEntry]
    implicit def hashEntryFormat: DynamoFormat[HashEntry] =
      DynamoFormat[HashEntry]

    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val dynamoClient =
      DynamoBuilder.buildDynamoClient(config)

    val s3Store =
      S3TypedStore[TransformedBaseWork]
    val dynamoIndexStore =
      new DynamoHashStore[String, Int, IndexEntry](dynamoConfig)

    new RecorderVhs(
      new RecorderHybridStore(objectLocationPrefix, dynamoIndexStore, s3Store)
    )
  }
}
