package uk.ac.wellcome.platform.goobi_reader

import java.time.Instant

import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.platform.goobi_reader.fixtures.GoobiReaderFixtures
import uk.ac.wellcome.platform.goobi_reader.models.GoobiRecordMetadata

class GoobiReaderFeatureTest
    extends FunSpec
    with Eventually
    with Matchers
    with GoobiReaderFixtures {
  private val eventTime = Instant.parse("2018-01-01T01:00:00.000Z")

  it("gets an S3 notification and puts the new record in VHS") {
    val s3Store = createStore

    withGoobiReaderWorkerService(s3Store) {
      case (QueuePair(queue, _), _, dao, store) =>
        val id = "mets-0001"
        val contents = "muddling the machinations of morose METS"

        val location = putString(s3Store, id, contents)

        sendNotificationToSQS(
          queue = queue,
          body = anS3Notification(location, eventTime)
        )

        eventually {
          assertRecordStored(
            id = id,
            expectedMetadata = GoobiRecordMetadata(eventTime),
            version = 1,
            expectedContents = contents,
            dao = dao,
            store = store
          )
        }
    }
  }
}
