package uk.ac.wellcome.platform.transformer.mets.service

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.mets.fixtures.MetsGenerators
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import WorkState.Source
import uk.ac.wellcome.pipeline_storage.MemoryIndexer
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures

import scala.collection.mutable

class MetsTransformerWorkerServiceTest
    extends AnyFunSpec
    with MetsGenerators
    with S3Fixtures
    with PipelineStorageStreamFixtures {

  it("retrieves a mets file from s3 and sends an invisible work") {
    val identifier = randomAlphanumeric(10)
    val version = randomInt(1, 10)
    val str = metsXmlWith(identifier, Some(License.CCBYNC))

    withWorkerService {
      case (QueuePair(queue, _), metsBucket, messageSender, vhs) =>
        val now = Instant.now
        sendWork(str, "mets.xml", vhs, metsBucket, queue, version, now)
        eventually {
          val works = messageSender.getMessages[Work.Invisible[Source]]
          works.head shouldBe expectedWork(identifier, version, now)

          assertQueueEmpty(queue)
        }
    }
  }

  it("sends failed messages to the dlq") {
    val version = randomInt(1, 10)
    val value1 = "this is not a valid mets file"

    withWorkerService {
      case (QueuePair(queue, dlq), metsBucket, messageSender, vhs) =>
        sendWork(
          value1,
          "mets.xml",
          vhs,
          metsBucket,
          queue,
          version,
          Instant.now)
        eventually {
          messageSender.getMessages[Work.Invisible[Source]] shouldBe empty

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it("sends messages that can be decoded as a Work[Source]") {

    val identifier = randomAlphanumeric(10)
    val version = randomInt(1, 10)
    val str = metsXmlWith(identifier, Some(License.CCBYNC))

    withWorkerService {
      case (QueuePair(queue, _), metsBucket, messageSender, dynamoStore) =>
        val now = Instant.now
        sendWork(str, "mets.xml", dynamoStore, metsBucket, queue, version, now)
        eventually {
          val works = messageSender.getMessages[Work[Source]]
          works.head shouldBe expectedWork(identifier, version, now)
        }
    }
  }

  private def expectedWork(identifier: String,
                           version: Int,
                           createdDate: Instant): Work.Invisible[Source] = {
    val expectedUrl =
      s"https://wellcomelibrary.org/iiif/$identifier/manifest"
    val expectedDigitalLocation = DigitalLocationDeprecated(
      expectedUrl,
      LocationType("iiif-presentation"),
      license = Some(License.CCBYNC))
    val expectedItem =
      Item(
        id = IdState.Unidentifiable,
        locations = List(expectedDigitalLocation))

    val expectedWork = Work.Invisible[Source](
      version = version,
      state = Source(
        SourceIdentifier(
          identifierType = IdentifierType("mets", "METS"),
          ontologyType = "Work",
          value = identifier
        ),
        createdDate
      ),
      data = WorkData[DataState.Unidentified](
        items = List(expectedItem),
        mergeCandidates = List(
          MergeCandidate(
            identifier = SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = identifier
            ),
            reason = "METS work"
          )
        )
      )
    )
    expectedWork
  }

  def withWorkerService[R](
    testWith: TestWith[(QueuePair,
                        Bucket,
                        MemoryMessageSender,
                        VersionedStore[String, Int, MetsLocation]),
                       R]): R =
    withLocalSqsQueuePair() {
      case queuePair @ QueuePair(queue, _) =>
        val messageSender = new MemoryMessageSender()
        val adapterStore =
          MemoryVersionedStore[String, MetsLocation](initialEntries = Map())
        val s3TypedStore = S3TypedStore[String]

        withLocalS3Bucket { metsBucket =>
          withPipelineStream(
            queue = queue,
            indexer = new MemoryIndexer[Work[Source]](
              index = mutable.Map[String, Work[Source]]()
            )
          ) { pipelineStream =>
            val workerService = new MetsTransformerWorkerService(
              pipelineStream = pipelineStream,
              sender = messageSender,
              adapterStore = adapterStore,
              metsXmlStore = s3TypedStore
            )

            workerService.run()

            testWith((queuePair, metsBucket, messageSender, adapterStore))
          }
        }
    }

  private def sendWork(mets: String,
                       name: String,
                       dynamoStore: VersionedStore[String, Int, MetsLocation],
                       metsBucket: Bucket,
                       queue: SQS.Queue,
                       version: Int,
                       createdDate: Instant): SendMessageResponse = {
    val rootPath = "data"
    val key = for {
      _ <- S3TypedStore[String].put(
        S3ObjectLocation(metsBucket.name, key = s"$rootPath/$name"))(mets)
      entry = MetsLocation(
        bucket = metsBucket.name,
        path = rootPath,
        version = version,
        file = name,
        createdDate = createdDate,
        manifestations = List())
      key <- dynamoStore.put(Version(name, version))(entry)
    } yield key

    key match {
      case Left(err)                 => throw new Exception(s"Failed storing work in VHS: $err")
      case Right(Identified(key, _)) => sendNotificationToSQS(queue, key)
    }
  }
}
