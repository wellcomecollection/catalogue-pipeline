package weco.pipeline.merger.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.work.Relations
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkFsm._
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Identified, Merged}
import weco.catalogue.internal_model.work.generators.MiroWorkGenerators
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.monitoring.memory.MemoryMetrics
import weco.pipeline.matcher.generators.MergeCandidateGenerators
import weco.pipeline.matcher.models.MatcherResult._
import weco.pipeline.merger.fixtures.{MatcherResultFixture, MergerFixtures}
import weco.pipeline_storage.memory.MemoryRetriever

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MergerWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MiroWorkGenerators
    with MatcherResultFixture
    with MergeCandidateGenerators
    with MergerFixtures {

  it(
    "reads matcher result messages, retrieves the works and sends on the IDs"
  ) {
    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, metrics, index) =>
        val work1 = identifiedWork()
        val work2 = identifiedWork()
        val work3 = identifiedWork()

        val matcherResult =
          createMatcherResultWith(Set(Set(work3), Set(work1, work2)))

        retriever.index ++= Map(
          work1.id -> work1,
          work2.id -> work2,
          work3.id -> work3
        )

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender) should contain only (
            work1.id,
            work2.id,
            work3.id
          )

          index shouldBe Map(
            work1.id -> Left(Right(
              work1.transition[Merged](matcherResult.createdTime).transition[Denormalised](Relations.none)
            )),
            work2.id -> Left(Right(
              work2.transition[Merged](matcherResult.createdTime).transition[Denormalised](Relations.none)
            )),
            work3.id -> Left(Right(
              work3.transition[Merged](matcherResult.createdTime).transition[Denormalised](Relations.none)
            ))
          )

          metrics.incrementedCounts.length should be >= 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("sends InvisibleWorks unmerged") {
    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, metrics, index) =>
        val work = identifiedWork().invisible()

        val matcherResult = createMatcherResultWith(Set(Set(work)))

        retriever.index ++= Map(work.id -> work)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender) should contain only work.id

          index shouldBe Map(
            work.id -> Left(Right(
              work.transition[Merged](matcherResult.createdTime).transition[Denormalised](Relations.none)
            ))
          )

          metrics.incrementedCounts.length shouldBe 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  it("fails if the matcher result refers to a non-existent work") {
    withMergerWorkerServiceFixtures {
      case (_, QueuePair(queue, dlq), senders, metrics, _) =>
        val work = identifiedWork()

        val matcherResult = createMatcherResultWith(Set(Set(work)))

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)

          getWorksSent(senders.workSender) shouldBe empty

          metrics.incrementedCounts.length shouldBe 3
          metrics.incrementedCounts.last should endWith("_failure")
        }
    }
  }

  it("sends a Work even if the matched version is not the latest") {
    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, _, index) =>
        val workA = identifiedWork()
        val workB = identifiedWork()
        val newerWorkB =
          identifiedWork(canonicalId = workB.state.canonicalId)
            .withVersion(workB.version + 1)
        val matcherResult = createMatcherResultWith(Set(Set(workA, workB)))

        retriever.index ++= Map(workA.id -> workA, workB.id -> newerWorkB)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender) should contain allOf (workA.id, workB.id)

          index shouldBe Map(
            workA.id -> Left(Right(
              workA.transition[Merged](matcherResult.createdTime).transition[Denormalised](Relations.none)
            )),
            workB.id -> Left(Right(
              newerWorkB.transition[Merged](matcherResult.createdTime).transition[Denormalised](Relations.none)
            ))
          )
        }
    }
  }

  it(
    "if it merges two Works, it sends two onward results (one merged, one redirected)"
  ) {
    val (digitisedWork, physicalWork) = sierraIdentifiedWorkPair()

    val works = List(physicalWork, digitisedWork)

    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, _, index) =>
        retriever.index ++= Map(
          physicalWork.id -> physicalWork,
          digitisedWork.id -> digitisedWork
        )

        val matcherResult = createMatcherResultWith(Set(works.toSet))

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender) should have size 2

          index should have size 2

          val redirectedWorks = index.collect {
            case (_, Left(Right(work: Work.Redirected[Denormalised]))) => work
          }
          val mergedWorks = index.collect {
            case (_, Left(Right(work: Work.Visible[Denormalised]))) => work
          }

          redirectedWorks should have size 1
          redirectedWorks.head.sourceIdentifier shouldBe digitisedWork.sourceIdentifier
          redirectedWorks.head.redirectTarget shouldBe IdState.Identified(
            sourceIdentifier = physicalWork.sourceIdentifier,
            canonicalId = physicalWork.state.canonicalId
          )

          mergedWorks should have size 1
          mergedWorks.head.sourceIdentifier shouldBe physicalWork.sourceIdentifier
        }
    }
  }

  it("sends an image, a merged work, and redirected works") {
    val (digitisedWork, physicalWork) = sierraIdentifiedWorkPair()
    val miroWork = miroIdentifiedWork()

    val works =
      List(physicalWork, digitisedWork, miroWork)

    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, _, index) =>
        retriever.index ++= Map(
          physicalWork.id -> physicalWork,
          digitisedWork.id -> digitisedWork,
          miroWork.id -> miroWork
        )

        val matcherResult = createMatcherResultWith(Set(works.toSet))

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender).distinct should have size 3

          index should have size 4

          val imagesSent = getImagesSent(senders.images).distinct
          imagesSent should have size 1

          val redirectedWorks = index.collect {
            case (_, Left(Right(work: Work.Redirected[Denormalised]))) => work
          }
          val mergedWorks = index.collect {
            case (_, Left(Right(work: Work.Visible[Denormalised]))) => work
          }
          val images = index.collect {
            case (_, Right(image)) => image
          }

          redirectedWorks should have size 2
          redirectedWorks.map(_.sourceIdentifier) should contain only
            (digitisedWork.sourceIdentifier, miroWork.sourceIdentifier)
          redirectedWorks.map(_.redirectTarget) should contain only
            IdState.Identified(
              sourceIdentifier = physicalWork.sourceIdentifier,
              canonicalId = physicalWork.state.canonicalId
            )

          mergedWorks should have size 1
          mergedWorks.head.sourceIdentifier shouldBe physicalWork.sourceIdentifier

          CanonicalId(
            imagesSent.head
          ) shouldBe miroWork.data.imageData.head.id.canonicalId
          images should have size 1
          CanonicalId(
            images.head.id
          ) shouldBe miroWork.data.imageData.head.id.canonicalId
        }
    }
  }

  it("splits the received works into multiple merged works if required") {
    val (digitisedWork1, physicalWork1) = sierraIdentifiedWorkPair()
    val (digitisedWork2, physicalWork2) = sierraIdentifiedWorkPair()

    val workPair1 = List(physicalWork1, digitisedWork1)
    val workPair2 = List(physicalWork2, digitisedWork2)

    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, _, index) =>
        retriever.index ++= Map(
          physicalWork1.id -> physicalWork1,
          digitisedWork1.id -> digitisedWork1,
          physicalWork2.id -> physicalWork2,
          digitisedWork2.id -> digitisedWork2
        )

        val matcherResult = createMatcherResultWith(
          Set(
            workPair1.toSet,
            workPair2.toSet
          )
        )

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender) should have size 4

          val redirectedWorks = index.collect {
            case (_, Left(Right(work: Work.Redirected[Denormalised]))) => work
          }
          val mergedWorks = index.collect {
            case (_, Left(Right(work: Work.Visible[Denormalised]))) => work
          }

          redirectedWorks should have size 2
          mergedWorks should have size 2
        }
    }
  }

  it("passes through a deleted work unmodified") {
    val deletedWork = sierraDigitalIdentifiedWork().deleted()
    val visibleWork = sierraPhysicalIdentifiedWork()
      .mergeCandidates(
        List(createSierraPairMergeCandidateFor(deletedWork))
      )

    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, _, index) =>
        retriever.index ++= Map(
          visibleWork.id -> visibleWork,
          deletedWork.id -> deletedWork
        )

        val matcherResult = createMatcherResultWith(
          Set(Set(visibleWork, deletedWork))
        )

        sendNotificationToSQS(queue = queue, message = matcherResult)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender) should have size 2

          val visibleWorks = index.collect {
            case (_, Left(Right(work: Work.Visible[Denormalised]))) => work
          }
          val deletedWorks = index.collect {
            case (_, Left(Right(work: Work.Deleted[Denormalised]))) => work
          }

          visibleWorks.map { _.id } shouldBe Seq(visibleWork.id)
          deletedWorks.map { _.id } shouldBe Seq(deletedWork.id)
        }
    }
  }

  it("fails if the message sent is not a matcher result") {
    withMergerWorkerServiceFixtures {
      case (_, QueuePair(queue, dlq), _, metrics, _) =>
        sendInvalidJSONto(queue)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
          metrics.incrementedCounts.length shouldBe 3
          metrics.incrementedCounts.last should endWith("_jsonDecodingFailure")
        }
    }
  }

  it("sends messages even if the works are outdated") {
    withMergerWorkerServiceFixtures {
      case (retriever, QueuePair(queue, dlq), senders, metrics, index) =>
        val work0 = identifiedWork().withVersion(0)
        val work1 = identifiedWork(canonicalId = work0.state.canonicalId)
          .withVersion(1)
        val matcherResult = createMatcherResultWith(Set(Set(work0)))
        retriever.index ++= Map(work1.id -> work1)

        sendNotificationToSQS(
          queue = queue,
          message = matcherResult
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          getWorksSent(senders.workSender) shouldBe List(work1.id)

          index shouldBe Map(
            work1.id -> Left(Right(
              work1.transition[Merged](matcherResult.createdTime).transition[Denormalised](Relations.none)
            ))
          )

          metrics.incrementedCounts.length shouldBe 1
          metrics.incrementedCounts.last should endWith("_success")
        }
    }
  }

  case class Senders(
    workSender: MemoryMessageSender,
    pathSender: MemoryMessageSender,
    pathConcatenatorSender: MemoryMessageSender,
    images: MemoryMessageSender
  )

  def withMergerWorkerServiceFixtures[R](
    testWith: TestWith[
      (
        MemoryRetriever[Work[Identified]],
        QueuePair,
        Senders,
        MemoryMetrics,
        mutable.Map[String, WorkOrImage]
      ),
      R
    ]
  ): R =
    withLocalSqsQueuePair(visibilityTimeout = 1 second) {
      case queuePair @ QueuePair(queue, _) =>
        val workRouter = new MemoryWorkRouter(
          workSender = new MemoryMessageSender(),
          pathSender = new MemoryMessageSender(),
          pathConcatenatorSender = new MemoryMessageSender()
        )
        val imageSender = new MemoryMessageSender()

        val retriever = new MemoryRetriever[Work[Identified]]()

        val metrics = new MemoryMetrics()
        val index = mutable.Map.empty[String, WorkOrImage]

        withMergerService(
          retriever,
          queue,
          workRouter,
          imageSender,
          metrics,
          index
        ) {
          _ =>
            testWith(
              (
                retriever,
                queuePair,
                Senders(
                  workRouter.workSender,
                  workRouter.pathSender,
                  workRouter.pathConcatenatorSender,
                  imageSender
                ),
                metrics,
                index
              )
            )
        }
    }

  def getWorksSent(senders: Senders): Seq[String] = {
    getWorksSent(senders.workSender)
  }

  def getPathsSent(senders: Senders): Seq[String] =
    getPathsSent(senders.pathSender)

  def getIncompletePathsSent(senders: Senders): Seq[String] =
    getIncompletePathSent(senders.pathConcatenatorSender)

  def getImagesSent(senders: Senders): Seq[String] =
    getImagesSent(senders.images)
}
