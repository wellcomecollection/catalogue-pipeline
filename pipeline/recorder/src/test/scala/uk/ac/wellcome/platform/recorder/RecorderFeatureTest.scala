package uk.ac.wellcome.platform.recorder

import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture

class RecorderFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with WorkerServiceFixture
    with WorksGenerators {

  it("receives a transformed Work, and saves it to the VHS") {
    val work = createUnidentifiedWork

    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, messageSender, queue) { _ =>
        sendMessage[TransformedBaseWork](queue = queue, obj = work)

        eventually {
          assertStoredSingleWork(
            messageSender = messageSender,
            dao = dao,
            store = store,
            expectedWork = work
          )
        }
      }
    }
  }
}
