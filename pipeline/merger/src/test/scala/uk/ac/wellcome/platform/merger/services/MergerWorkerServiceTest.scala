package uk.ac.wellcome.platform.merger.services

import com.amazonaws.services.cloudwatch.model.StandardUnit
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.fixtures.{
  MatcherResultFixture,
  WorkerServiceFixture
}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.monitoring.memory.MemoryMetrics

class MergerWorkerServiceTest
    extends FunSpec
    with ScalaFutures
    with IntegrationPatience
    with BigMessagingFixture
    with WorksGenerators
    with MatcherResultFixture
    with Matchers
    with MockitoSugar
    with WorkerServiceFixture {

  it(
    "reads matcher result messages, retrieves the works from vhs and sends them to sns") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), topics, metrics) =>
        val work1 = createUnidentifiedWork
        val work2 = createUnidentifiedWork
        val work3 = createUnidentifiedWork

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

          val worksSent = getMessages[BaseWork](topics.works)
          worksSent should contain only (work1, work2, work3)

          metrics.incrementedCounts.length should be >= 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("sends InvisibleWorks unmerged") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), topics, metrics) =>
        val work = createUnidentifiedInvisibleWork

        val matcherResult = matcherResultWith(Set(Set(work)))

        givenStoredInVhs(vhs, work)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = getMessages[BaseWork](topics.works)
          worksSent should contain only work

          metrics.incrementedCounts.length shouldBe 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("fails if the work is not in vhs") {
    withMergerWorkerServiceFixtures {
      case (_, QueuePair(queue, dlq), topics, metrics) =>
        val work = createUnidentifiedWork

        val matcherResult = matcherResultWith(Set(Set(work)))

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
          listMessagesReceivedFromSNS(topics.works) shouldBe empty

          metrics.incrementedCounts.length shouldBe 3
          metrics.incrementedCounts.last should endWith("_failure")
        }
    }
  }

  it("discards works with newer versions in vhs, sends along the others") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), topics, _) =>
        val work = createUnidentifiedWork
        val olderWork = createUnidentifiedWork
        val newerWork = olderWork.copy(version = 2)

        val matcherResult = matcherResultWith(Set(Set(work, olderWork)))

        givenStoredInVhs(vhs, work, newerWork)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val worksSent = getMessages[BaseWork](topics.works)
          worksSent should contain only work
        }
    }
  }

  it("discards works with version 0 and sends along the others") {
    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), topics, metrics) =>
        val versionZeroWork = createUnidentifiedWorkWith(version = 0)
        val work = versionZeroWork
          .copy(version = 1)

        val matcherResult = matcherResultWith(Set(Set(work, versionZeroWork)))

        givenStoredInVhs(vhs, work)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val worksSent = getMessages[BaseWork](topics.works)
          worksSent should contain only work

          metrics.incrementedCounts.length shouldBe 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("sends a merged work and a redirected work to SQS") {
    val physicalWork = createSierraPhysicalWork
    val digitalWork = createSierraDigitalWork

    val works = List(physicalWork, digitalWork)

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), topics, _) =>
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

          val worksSent = getMessages[BaseWork](topics.works).distinct
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

  it("sends an image, a merged work, and redirected works to SQS") {
    val physicalWork = createSierraPhysicalWork
    val digitalWork = createSierraDigitalWork
    val miroWork = createMiroWork

    val works = List(physicalWork, digitalWork, miroWork)

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), topics, _) =>
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

          val worksSent = getMessages[BaseWork](topics.works).distinct
          worksSent should have size 3

          val imagesSent =
            getMessages[MergedImage[Unminted]](topics.images).distinct
          imagesSent should have size 1

          val redirectedWorks = worksSent.collect {
            case work: UnidentifiedRedirectedWork => work
          }
          val mergedWorks = worksSent.collect {
            case work: UnidentifiedWork => work
          }

          redirectedWorks should have size 2
          redirectedWorks.map(_.sourceIdentifier) should contain only
            (digitalWork.sourceIdentifier, miroWork.sourceIdentifier)
          redirectedWorks.map(_.redirect) should contain only
            IdentifiableRedirect(physicalWork.sourceIdentifier)

          mergedWorks should have size 1
          mergedWorks.head.sourceIdentifier shouldBe physicalWork.sourceIdentifier

          imagesSent.head.id shouldBe miroWork.data.images.head.id
        }
    }
  }

  it("splits the received works into multiple merged works if required") {
    val workPair1 = List(createSierraPhysicalWork, createSierraDigitalWork)
    val workPair2 = List(createSierraPhysicalWork, createSierraDigitalWork)
    val works = workPair1 ++ workPair2

    withMergerWorkerServiceFixtures {
      case (vhs, QueuePair(queue, dlq), topics, _) =>
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

          val worksSent = getMessages[BaseWork](topics.works).distinct
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

  case class Topics(works: Topic, images: Topic)

  def withMergerWorkerServiceFixtures[R](
    testWith: TestWith[(VHS, QueuePair, Topics, MemoryMetrics[StandardUnit]),
                       R]): R =
    withVHS { vhs =>
      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withLocalSnsTopic { worksTopic =>
            withLocalSnsTopic { imagesTopic =>
              val metrics = new MemoryMetrics[StandardUnit]
              withWorkerService(vhs, queue, worksTopic, imagesTopic, metrics) {
                _ =>
                  testWith(
                    (
                      vhs,
                      QueuePair(queue, dlq),
                      Topics(worksTopic, imagesTopic),
                      metrics))
              }
            }
          }
      }
    }
}
