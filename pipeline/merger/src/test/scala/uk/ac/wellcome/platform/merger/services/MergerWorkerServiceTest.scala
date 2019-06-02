package uk.ac.wellcome.platform.merger.services

import org.mockito.Matchers.endsWith
import org.mockito.Mockito.{atLeastOnce, times, verify}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.merger.fixtures.{MatcherResultFixture, WorkerServiceFixture}
import uk.ac.wellcome.storage.streaming.CodecInstances._

class MergerWorkerServiceTest
    extends FunSpec
    with ScalaFutures
    with IntegrationPatience
    with WorksGenerators
    with MatcherResultFixture
    with Matchers
    with WorkerServiceFixture {

  it(
    "reads matcher result messages, retrieves the works from vhs and sends them to sns") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), messageSender, metricsSender) =>
        val work1 = createUnidentifiedWork
        val work2 = createUnidentifiedWork
        val work3 = createUnidentifiedWork

        val matcherResult =
          matcherResultWith(Set(Set(work3), Set(work1, work2)))

        storeInVhs(vhs, work1, work2, work3)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = messageSender.getMessages[BaseWork]
          worksSent should contain only (work1, work2, work3)

          verify(metricsSender, atLeastOnce)
            .incrementCount(endsWith("_success"))
        }
    }
  }

  it("sends InvisibleWorks unmerged") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), messageSender, metricsSender) =>
        val work = createUnidentifiedInvisibleWork

        val matcherResult = matcherResultWith(Set(Set(work)))

        storeInVhs(vhs, work)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = messageSender.getMessages[BaseWork]
          worksSent should contain only work

          verify(metricsSender, times(1))
            .incrementCount(endsWith("_success"))
        }
    }
  }

  it("fails if the work is not in vhs") {
    withMergerWorkerServiceFixtures {
      case (_, QueuePair(queue, dlq), messageSender, metricsSender) =>
        val work = createUnidentifiedWork

        val matcherResult = matcherResultWith(Set(Set(work)))

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
          
          messageSender.messages shouldBe empty

          verify(metricsSender, times(3))
            .incrementCount(endsWith("_failure"))
        }
    }
  }

  it("discards works with newer versions in vhs, sends along the others") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), messageSender, _) =>
        val work = createUnidentifiedWork
        val olderWork = createUnidentifiedWork
        val newerWork = olderWork.copy(version = 2)

        val matcherResult = matcherResultWith(Set(Set(work, olderWork)))

        storeInVhs(vhs, work, newerWork)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = messageSender.getMessages[BaseWork]
          worksSent should contain only work
        }
    }
  }

  it("discards works with version 0 and sends along the others") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), messageSender, metricsSender) =>
        val versionZeroWork = createUnidentifiedWorkWith(version = 0)
        val work = versionZeroWork
          .copy(version = 1)

        val matcherResult = matcherResultWith(Set(Set(work, versionZeroWork)))

        storeInVhs(vhs, work)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = messageSender.getMessages[BaseWork]
          worksSent should contain only work

          verify(metricsSender, times(1))
            .incrementCount(endsWith("_success"))
        }
    }
  }

  it("sends a merged work and a redirected work to SQS") {
    val physicalWork = createSierraPhysicalWork
    val digitalWork = createSierraDigitalWork

    val works = List(physicalWork, digitalWork)

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), messageSender, _) =>
        storeInVhs(vhs, works: _*)

        val matcherResult = MatcherResult(
          Set(
            MatchedIdentifiers(worksToWorkIdentifiers(works))
          )
        )

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = messageSender.getMessages[BaseWork]
          worksSent should have size 2

          val redirectedWorks = worksSent.collect {
            case work: UnidentifiedRedirectedWork => work
          }
          val mergedWorks = worksSent.collect {
            case work: UnidentifiedWork => work
          }

          redirectedWorks should have size 1
          redirectedWorks.head.sourceIdentifier shouldBe digitalWork.sourceIdentifier
          redirectedWorks.head.redirect shouldBe IdentifiableRedirect(
            physicalWork.sourceIdentifier)

          mergedWorks should have size 1
          mergedWorks.head.sourceIdentifier shouldBe physicalWork.sourceIdentifier
        }
    }
  }

  it("splits the received works into multiple merged works if required") {
    val workPair1 = List(createSierraPhysicalWork, createSierraDigitalWork)
    val workPair2 = List(createSierraPhysicalWork, createSierraDigitalWork)
    val works = workPair1 ++ workPair2

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), messageSender, _) =>
        storeInVhs(vhs, works: _*)

        val matcherResult = MatcherResult(
          Set(
            MatchedIdentifiers(worksToWorkIdentifiers(workPair1)),
            MatchedIdentifiers(worksToWorkIdentifiers(workPair2))
          ))

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = messageSender.getMessages[BaseWork]
          worksSent should have size 4

          val redirectedWorks = worksSent.collect {
            case work: UnidentifiedRedirectedWork => work
          }
          val mergedWorks = worksSent.collect {
            case work: UnidentifiedWork => work
          }

          redirectedWorks should have size 2
          mergedWorks should have size 2
        }
    }
  }

  it("fails if the message sent is not a matcher result") {
    withMergerWorkerServiceFixtures {
      case (_, QueuePair(queue, dlq), _, metricsSender) =>
        sendInvalidJSONto(queue)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
          verify(metricsSender, times(3))
            .incrementCount(endsWith("_recognisedFailure"))
        }
    }
  }

  def withMergerWorkerServiceFixtures[R](
    testWith: TestWith[
      (RecorderVhs, QueuePair, MemoryBigMessageSender[BaseWork], MetricsSender),
      R]): R =
      withLocalSqsQueueAndDlq {
        case queuePair@QueuePair(queue, _) =>
          withMockMetricsSender { mockMetricsSender =>
            val vhs = createVhs()
            val messageSender = new MemoryBigMessageSender[BaseWork]()
            withWorkerService(
              vhs = vhs,
              messageSender = messageSender,
              queue = queue,
              metricsSender = mockMetricsSender) { _ =>
              testWith((vhs, queuePair, messageSender, mockMetricsSender))
            }
          }
      }
}
