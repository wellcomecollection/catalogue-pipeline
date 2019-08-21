package uk.ac.wellcome.platform.recorder

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture
import uk.ac.wellcome.storage.{Version, Identified}
import uk.ac.wellcome.storage.store.HybridStoreEntry

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture

class RecorderFeatureTest
    extends FunSpec
    with Matchers
    with IntegrationPatience
    with BigMessagingFixture
    with WorkerServiceFixture
    with WorksGenerators {

  it("receives a transformed Work, and saves it to the VHS") {
    val work = createUnidentifiedWork

    withLocalSqsQueue { queue =>
      withLocalS3Bucket { bucket =>
        withLocalSnsTopic { topic =>
          withWorkerService(bucket, topic, queue) { case (_, vhs) =>
            sendMessage[TransformedBaseWork](queue = queue, obj = work)

            eventually {
              val key = Version(work.sourceIdentifier.toString, 0)
              vhs.get(key) shouldBe
                Right(Identified(key, HybridStoreEntry(work, EmptyMetadata())))
            }
          }
        }
      }
    }
  }
}
