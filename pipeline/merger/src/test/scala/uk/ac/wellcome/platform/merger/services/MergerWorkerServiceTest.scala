package uk.ac.wellcome.platform.merger.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.merger.fixtures.{
  MatcherResultFixture,
  WorkerServiceFixture
}
import WorkState.Merged
import WorkFsm._
import uk.ac.wellcome.models.work.generators.MiroWorkGenerators

class MergerWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MiroWorkGenerators
    with MatcherResultFixture
    with WorkerServiceFixture {

  it(
    "reads matcher result messages, retrieves the works from vhs and sends them to sns") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), senders, metrics) =>
        val work1 = sourceWork()
        val work2 = sourceWork()
        val work3 = sourceWork()

        val matcherResult =
          matcherResultWith(Set(Set(work3), Set(work1, work2)))

        givenStoredInVhs(vhs, work1, work2, work3)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          senders.works
            .getMessages[Work[Merged]] should contain only (
            work1.transition[Merged](1),
            work2.transition[Merged](1),
            work3.transition[Merged](1)
          )

          metrics.incrementedCounts.length should be >= 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("sends InvisibleWorks unmerged") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), senders, metrics) =>
        val work = sourceWork().invisible()

        val matcherResult = matcherResultWith(Set(Set(work)))

        givenStoredInVhs(vhs, work)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          senders.works.getMessages[Work[Merged]] should contain only
            work.transition[Merged](1)

          metrics.incrementedCounts.length shouldBe 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("fails if the work is not in vhs") {
    withMergerWorkerServiceFixtures {
      case (_, QueuePair(queue, dlq), senders, metrics) =>
        val work = sourceWork()

        val matcherResult = matcherResultWith(Set(Set(work)))

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)

          senders.works.messages shouldBe empty

          metrics.incrementedCounts.length shouldBe 3
          metrics.incrementedCounts.last should endWith("_failure")
        }
    }
  }

  it("discards works with newer versions in vhs, sends along the others") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), senders, _) =>
        val work = sourceWork()
        val olderWork = sourceWork()
        val newerWork =
          sourceWork(sourceIdentifier = olderWork.sourceIdentifier)
            .withVersion(olderWork.version + 1)

        val matcherResult = matcherResultWith(Set(Set(work, olderWork)))

        givenStoredInVhs(vhs, work, newerWork)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val worksSent = senders.works.getMessages[Work[Merged]]
          worksSent should contain only
            work.transition[Merged](1)
        }
    }
  }

  it("discards works with version 0 and sends along the others") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), senders, metrics) =>
        val versionZeroWork =
          sourceWork()
            .withVersion(0)

        val work =
          sourceWork(sourceIdentifier = versionZeroWork.sourceIdentifier)
            .withVersion(1)

        val matcherResult = matcherResultWith(Set(Set(work, versionZeroWork)))

        givenStoredInVhs(vhs, work)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = senders.works.getMessages[Work[Merged]]
          worksSent should contain only
            work.transition[Merged](1)

          metrics.incrementedCounts.length shouldBe 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("sends a merged work and a redirected work to SQS") {
    val (digitisedWork, physicalWork) = sierraSourceWorkPair()

    val works = List(physicalWork, digitisedWork)

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), senders, _) =>
        givenStoredInVhs(vhs, works: _*)

        val matcherResult = MatcherResult(
          Set(
            MatchedIdentifiers(worksToWorkIdentifiers(works))
          )
        )

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = senders.works.getMessages[Work[Merged]].distinct
          worksSent should have size 2

          val redirectedWorks = worksSent.collect {
            case work: Work.Redirected[Merged] => work
          }
          val mergedWorks = worksSent.collect {
            case work: Work.Visible[Merged] => work
          }

          redirectedWorks should have size 1
          redirectedWorks.head.sourceIdentifier shouldBe digitisedWork.sourceIdentifier
          redirectedWorks.head.redirect shouldBe IdState.Identifiable(
            physicalWork.sourceIdentifier)

          mergedWorks should have size 1
          mergedWorks.head.sourceIdentifier shouldBe physicalWork.sourceIdentifier
        }
    }
  }

  it("sends an image, a merged work, and redirected works to SQS") {
    val (digitisedWork, physicalWork) = sierraSourceWorkPair()
    val miroWork = miroSourceWork()

    val works =
      List(physicalWork, digitisedWork, miroWork)

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), senders, _) =>
        givenStoredInVhs(vhs, works: _*)

        val matcherResult = MatcherResult(
          Set(
            MatchedIdentifiers(worksToWorkIdentifiers(works))
          )
        )

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = senders.works.getMessages[Work[Merged]].distinct
          worksSent should have size 3

          val imagesSent =
            senders.images
              .getMessages[MergedImage[DataState.Unidentified]]
              .distinct
          imagesSent should have size 1

          val redirectedWorks = worksSent.collect {
            case work: Work.Redirected[Merged] => work
          }
          val mergedWorks = worksSent.collect {
            case work: Work.Visible[Merged] => work
          }

          redirectedWorks should have size 2
          redirectedWorks.map(_.sourceIdentifier) should contain only
            (digitisedWork.sourceIdentifier, miroWork.sourceIdentifier)
          redirectedWorks.map(_.redirect) should contain only
            IdState.Identifiable(physicalWork.sourceIdentifier)

          mergedWorks should have size 1
          mergedWorks.head.sourceIdentifier shouldBe physicalWork.sourceIdentifier

          imagesSent.head.id shouldBe miroWork.data.images.head.id
        }
    }
  }

  it("splits the received works into multiple merged works if required") {
    val (digitisedWork1, physicalWork1) = sierraSourceWorkPair()
    val (digitisedWork2, physicalWork2) = sierraSourceWorkPair()

    val workPair1 = List(physicalWork1, digitisedWork1)
    val workPair2 = List(physicalWork2, digitisedWork2)

    val works = workPair1 ++ workPair2

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), senders, _) =>
        givenStoredInVhs(vhs, works: _*)

        val matcherResult = MatcherResult(
          Set(
            MatchedIdentifiers(worksToWorkIdentifiers(workPair1)),
            MatchedIdentifiers(worksToWorkIdentifiers(workPair2))
          ))

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = senders.works.getMessages[Work[Merged]]
          worksSent should have size 4

          val redirectedWorks = worksSent.collect {
            case work: Work.Redirected[Merged] => work
          }
          val mergedWorks = worksSent.collect {
            case work: Work.Visible[Merged] => work
          }

          redirectedWorks should have size 2
          mergedWorks should have size 2
        }
    }
  }

  it("fails if the message sent is not a matcher result") {
    withMergerWorkerServiceFixtures {
      case (_, QueuePair(queue, dlq), _, metrics) =>
        sendInvalidJSONto(queue)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
          metrics.incrementedCounts.length shouldBe 3
          metrics.incrementedCounts.last should endWith("_recognisedFailure")
        }
    }
  }

  case class Senders(works: MemoryMessageSender, images: MemoryMessageSender)

  def withMergerWorkerServiceFixtures[R](
    testWith: TestWith[(VHS, QueuePair, Senders, MemoryMetrics[StandardUnit]),
                       R]): R =
    withVHS { vhs =>
      withLocalSqsQueuePair() {
        case queuePair @ QueuePair(queue, _) =>
          val workSender = new MemoryMessageSender()
          val imageSender = new MemoryMessageSender()

          val metrics = new MemoryMetrics[StandardUnit]

          withWorkerService(vhs, queue, workSender, imageSender, metrics) { _ =>
            testWith(
              (vhs, queuePair, Senders(workSender, imageSender), metrics)
            )
          }
      }
    }
}
