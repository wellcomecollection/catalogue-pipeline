package uk.ac.wellcome.platform.goobi_reader.fixtures

import java.io.InputStream
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

import org.scalatest.{Assertion, EitherValues, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.goobi_reader.models.GoobiRecordMetadata
import uk.ac.wellcome.platform.goobi_reader.services.GoobiReaderWorkerService
import uk.ac.wellcome.storage.{KeyPrefix, KeySuffix, ObjectLocation, WriteError}
import uk.ac.wellcome.storage.memory.{
  MemoryObjectStore,
  MemoryStorageBackend,
  MemoryVersionedDao
}
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.{Entry, VersionedHybridStore}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait GoobiReaderFixtures extends Matchers with SQS with EitherValues {

  private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter
      .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .withZone(ZoneOffset.UTC)

  def anS3Notification(objectLocation: ObjectLocation,
                       eventTime: Instant): String =
    s"""{
        | "Records": [
        |     {
        |         "eventVersion": "2.0",
        |         "eventSource": "aws:s3",
        |         "awsRegion": "eu-west-1",
        |         "eventTime": "${dateTimeFormatter.format(eventTime)}",
        |         "eventName": "ObjectCreated:Put",
        |         "userIdentity": {
        |             "principalId": "AWS:AIDAJCQOG2NMLPWL7OVGQ"
        |         },
        |         "requestParameters": {
        |             "sourceIPAddress": "195.143.129.132"
        |         },
        |         "responseElements": {
        |             "x-amz-request-id": "8AA72F36945DF84E",
        |             "x-amz-id-2": "0sF4Z82rcjaJ4WIKZ7OGEFgSNUZcSqH7J459r4KYBsZ6OyVhgpcbGNeM+wI6llctdLviN/8tgMo="
        |         },
        |         "s3": {
        |             "s3SchemaVersion": "1.0",
        |             "configurationId": "testevent",
        |             "bucket": {
        |                 "name": "${objectLocation.namespace}",
        |                 "ownerIdentity": {
        |                     "principalId": "A2BMUDSS9CMZ3O"
        |                 },
        |                 "arn": "arn:aws:s3:::${objectLocation.namespace}"
        |                 },
        |             "object": {
        |                 "key": "${objectLocation.key}",
        |                 "size": 7193624,
        |                 "eTag": "ce96ad12a0e92e97f9c89948967c62e2",
        |                 "sequencer": "005AFEDC82454E713A"
        |             }
        |         }
        |     }
        | ]
        |}""".stripMargin

  type GoobiStore = MemoryObjectStore[InputStream]
  type GoobiDao = MemoryVersionedDao[String, Entry[String, GoobiRecordMetadata]]
  type GoobiVhs = VersionedHybridStore[String, InputStream, GoobiRecordMetadata]

  def createDao: GoobiDao =
    MemoryVersionedDao[String, Entry[String, GoobiRecordMetadata]]()

  def createStore: GoobiStore = new GoobiStore() {
    override def put(namespace: String)(
      input: InputStream,
      keyPrefix: KeyPrefix,
      keySuffix: KeySuffix,
      userMetadata: Map[String, String]): Either[WriteError, ObjectLocation] = {
      val location =
        ObjectLocation(namespace, key = keyPrefix.value + keySuffix.value)
      storageBackend
        .put(
          location,
          inputStream = input,
          metadata = Map.empty
        )
        .map { _ =>
          location
        }
    }
  }

  def createVhs(dao: GoobiDao = createDao,
                store: GoobiStore = createStore): GoobiVhs =
    new GoobiVhs {
      override protected val versionedDao: GoobiDao = dao
      override protected val objectStore: GoobiStore = store
    }

  def assertRecordStored(id: String,
                         version: Int,
                         expectedMetadata: GoobiRecordMetadata,
                         expectedContents: String,
                         dao: GoobiDao,
                         store: GoobiStore): Assertion = {
    val storedEntry: Entry[String, GoobiRecordMetadata] = dao.entries(id)

    storedEntry.id shouldBe id
    storedEntry.version shouldBe version
    storedEntry.metadata shouldBe expectedMetadata

    val storedObject = store.storageBackend
      .asInstanceOf[MemoryStorageBackend]
      .storage(storedEntry.location)

    storedObject.s shouldBe expectedContents
  }

  def withGoobiReaderWorkerService[R](
    s3ObjectStore: MemoryObjectStore[InputStream] =
      new MemoryObjectStore[InputStream]())(
    testWith: TestWith[(QueuePair, MetricsSender, GoobiDao, GoobiStore), R])
    : R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, dlq) =>
          withMockMetricsSender { mockMetricsSender =>
            withSQSStream[NotificationMessage, R](
              queue = queue,
              metricsSender = mockMetricsSender) { sqsStream =>
              val dao = createDao
              val store = createStore

              val vhs = createVhs(dao, store)
              val service = new GoobiReaderWorkerService(
                s3ObjectStore = s3ObjectStore,
                sqsStream = sqsStream,
                vhs = vhs
              )

              service.run()

              testWith((queuePair, mockMetricsSender, dao, store))
            }
          }
      }
    }

  def stringStream(s: String): InputStream =
    stringCodec.toStream(s).right.value

  def putString(s3Store: MemoryObjectStore[InputStream],
                id: String,
                s: String): ObjectLocation = {
    val namespace = Random.alphanumeric.take(10) mkString

    val input = stringStream(s)

    s3Store
      .put(namespace)(
        input,
        keySuffix = KeySuffix(s"$id.xml")
      )
      .right
      .value
  }
}
