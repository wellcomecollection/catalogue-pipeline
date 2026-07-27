package weco.pipeline.merger

import org.scalatest.EitherValues
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.merger.fixtures.{MatcherResultFixture, MergerFixtures}
import weco.pipeline_storage.memory.MemoryRetriever

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class MergerFeatureTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with MergerFixtures
    with MatcherResultFixture
    with Eventually
    with EitherValues
    with IntegrationPatience {

  it("switches how a pair of works are matched") {
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
    //
    //      A ---> B
    //
    //      D <--> C
    //
    val workA_t0 =
      identifiedWork(canonicalId = idA, sourceModifiedTime = time(t = 0))
    val workB_t0 =
      identifiedWork(canonicalId = idB, sourceModifiedTime = time(t = 0))
    val workC_t0 =
      identifiedWork(canonicalId = idC, sourceModifiedTime = time(t = 0))
    val workD_t0 =
      identifiedWork(canonicalId = idD, sourceModifiedTime = time(t = 0))

    val mergedIndex = mutable.Map[String, WorkOrImage](
      idA.underlying -> Left(workA_t0.transition[Merged](time(t = 0))),
      idB.underlying -> Left(workB_t0.transition[Merged](time(t = 0))),
      idC.underlying -> Left(workC_t0.transition[Merged](time(t = 0))),
      idD.underlying -> Left(workD_t0.transition[Merged](time(t = 0)))
    )

    // TODO: Can we use a CanonicalId in the retriever?
    val retriever = new MemoryRetriever[Work[Identified]](
      index = mutable.Map[String, Work[Identified]](
        idA.underlying -> workA_t0,
        idB.underlying -> workB_t0,
        idC.underlying -> workC_t0,
        idD.underlying -> workD_t0
      )
    )

    withMergerProcessor(retriever, mergedIndex) {
      mergeProcessor =>
        // 2) Now we update all four works at times t=1, t=2, t=3 and t=4.
        // However, due to best-effort ordering, we don't process these updates
        // in the correct order.
        val workA_t1 =
          identifiedWork(
            canonicalId = idA,
            sourceModifiedTime = time(t = 1)
          )
            .title("I was updated at t = 1")
        val workB_t2 =
          identifiedWork(
            canonicalId = idB,
            sourceModifiedTime = time(t = 2)
          )
            .title("I was updated at t = 2")
        val workC_t3 =
          identifiedWork(
            canonicalId = idC,
            sourceModifiedTime = time(t = 3)
          )
            .title("I was updated at t = 3")
        val workD_t4 =
          identifiedWork(
            canonicalId = idD,
            sourceModifiedTime = time(t = 4)
          )
            .title("I was updated at t = 4")

        // 3) Suppose the update to D gets processed by the matcher at
        // time t=5, and it matches all four works together.
        //
        //      A ---> B
        //      ^
        //      |
        //      v
        //      D <--- C
        //
        // Send the corresponding matcher result to SQS.  Check that all four works
        // get updated.
        retriever.index(idD.underlying) = workD_t4

        val matcherResult_t5 = createMatcherResultWith(
          matchedEntries = Set(Set(workA_t0, workB_t0, workC_t0, workD_t4)),
          createdTime = time(t = 5)
        )

        val existingTimes_t5 = getModifiedTimes(mergedIndex)
        mergeProcessor.process(matcherResult_t5)

        eventually {
          val storedTimes_t5 = getModifiedTimes(mergedIndex)
          existingTimes_t5.foreach {
            case (id, time) =>
              storedTimes_t5(id) shouldBe >(time)
          }

          mergedIndex(idD.underlying).left.value.data.title shouldBe Some(
            "I was updated at t = 4"
          )
        }


        // 4) Now suppose the updates to A and C get processed by the matcher
        // at time t = 6.
        //
        //      A      B
        //      ^      ^
        //      |      |
        //      v      v
        //      D      C
        //
        // As before, send the matcher result to SQS and check that all four works
        // get updated.
        retriever.index(idA.underlying) = workA_t1
        retriever.index(idC.underlying) = workC_t3

        val matcherResult_t6 = createMatcherResultWith(
          matchedEntries =
            Set(Set(workA_t1, workD_t4), Set(workB_t0, workC_t3)),
          createdTime = time(t = 6)
        )

        val existingTimes_t6 = getModifiedTimes(mergedIndex)
        mergeProcessor.process(matcherResult_t6)

        eventually {
          val storedTimes_t6 = getModifiedTimes(mergedIndex)
          existingTimes_t6.foreach {
            case (id, time) =>
              storedTimes_t6(id) shouldBe >(time)
          }

          mergedIndex(idA.underlying).left.value.data.title shouldBe Some(
            "I was updated at t = 1"
          )
          mergedIndex(idC.underlying).left.value.data.title shouldBe Some(
            "I was updated at t = 3"
          )
        }

        // 5) Now suppose we finally process the update to B at time t=7.
        retriever.index(idB.underlying) = workB_t2

        val matcherResult_t7 = createMatcherResultWith(
          matchedEntries = Set(Set(workB_t2, workC_t3)),
          createdTime = time(t = 7)
        )

        val existingTimes_t7 = getModifiedTimes(mergedIndex)
        mergeProcessor.process(matcherResult_t7)

        eventually {
          val storedTimes_t7 = getModifiedTimes(mergedIndex)
          storedTimes_t7(idA) shouldBe existingTimes_t7(idA)
          storedTimes_t7(idD) shouldBe existingTimes_t7(idD)

          storedTimes_t7(idB) shouldBe >(existingTimes_t7(idB))
          storedTimes_t7(idC) shouldBe >(existingTimes_t7(idC))

          mergedIndex(idB.underlying).left.value.data.title shouldBe Some(
            "I was updated at t = 2"
          )
        }
    }
  }

  private def id(c: Char): CanonicalId =
    CanonicalId(c.toString * 8)

  private def time(t: Int): Instant =
    Instant.ofEpochSecond(t)

  private def getModifiedTimes(
    index: mutable.Map[String, WorkOrImage]
  ): Map[CanonicalId, Instant] =
    index.values.collect {
      case Left(work) => work.state.canonicalId -> work.state.mergedTime
    }.toMap
}
