package uk.ac.wellcome.platform.merger

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.pipeline_storage.MemoryRetriever
import uk.ac.wellcome.platform.merger.fixtures.WorkerServiceFixture
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class MergerFeatureTest extends AnyFunSpec with Matchers with WorkGenerators with WorkerServiceFixture {
  it("switches how a pair of works care matched") {
    // This test case is based on a real example of four Works that were
    // being matched correctly.  In particular, we had some Sanskrit manuscripts
    // where the METS work and e-bib were paired incorrectly.
    //
    // This led us to tweak how the merger assigns the "modifiedDate"
    // on the Works it creates.
    //
    // This is a feature test rather than a scenario test because we have
    // to simulate an exact sequence of actions around SQS ordering.
    //
    // This is based on the worked example described in RFC 038:
    // https://github.com/wellcomecollection/docs/tree/8d83d75aba89ead23559584db2533e95ceb09200/rfcs/038-matcher-versioning#the-problem-a-worked-example

    val idA = id('A')
    val idB = id('B')
    val idC = id('C')
    val idD = id('D')

    // 1) Start with four works, all created at time t = 0.  Assume further that
    // they have already been processed by the matcher/merger at time t = 0.
    val workA_t0 = identifiedWork(canonicalId = idA, modifiedTime = time(t = 0))
    val workB_t0 = identifiedWork(canonicalId = idB, modifiedTime = time(t = 0))
    val workC_t0 = identifiedWork(canonicalId = idC, modifiedTime = time(t = 0))
    val workD_t0 = identifiedWork(canonicalId = idD, modifiedTime = time(t = 0))

    val index = mutable.Map[String, WorkOrImage](
      idA.underlying -> Left(workA_t0.transition[Merged](time(t = 0))),
      idB.underlying -> Left(workB_t0.transition[Merged](time(t = 0))),
      idC.underlying -> Left(workC_t0.transition[Merged](time(t = 0))),
      idD.underlying -> Left(workD_t0.transition[Merged](time(t = 0))),
    )

    // TODO: Can we use a CanonicalId in the retriever?
    val retriever = new MemoryRetriever[Work[Identified]](
      index = mutable.Map[String, Work[Identified]](
        idA.underlying -> workA_t0,
        idB.underlying -> workB_t0,
        idC.underlying -> workC_t0,
        idD.underlying -> workD_t0,
      )
    )

    val workSender = new MemoryMessageSender

    withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
      withWorkerService(retriever, queue, workSender, index = index) { _ =>
        true shouldBe true
      }
    }
  }

  private def id(c: Char): CanonicalId =
    CanonicalId(c.toString * 8)

  private def time(t: Int): Instant =
    Instant.ofEpochSecond(t)
}
