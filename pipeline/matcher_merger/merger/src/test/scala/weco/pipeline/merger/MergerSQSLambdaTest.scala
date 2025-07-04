package weco.pipeline.merger

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.WorkFsm._
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.generators.MiroWorkGenerators
import weco.fixtures.TestWith
import weco.lambda.SQSLambdaMessage
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.matcher.generators.MergeCandidateGenerators
import weco.pipeline.merger.fixtures.{MatcherResultFixture, MergerFixtures}
import weco.pipeline_storage.memory.MemoryRetriever

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class MergerSQSLambdaTest
  extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with MiroWorkGenerators
    with MatcherResultFixture
    with MergeCandidateGenerators
    with MergerFixtures {


  it( "reads matcher result messages, retrieves the works and sends on the IDs") {
    val work1 = identifiedWork()
    val work2 = identifiedWork()
    val work3 = identifiedWork()
    val matcherResult =
      createMatcherResultWith(Set(Set(work3), Set(work1, work2)))

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
            messageId = randomUUID.toString,
            message = matcherResult
          ))
        identifiedIndex.index ++= Map(
          work1.id -> work1,
          work2.id -> work2,
          work3.id -> work3
        )

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender) should contain only (
              work1.id,
              work2.id,
              work3.id
            )

            mergedIndex shouldBe Map(
              work1.id -> Left(
                work1.transition[Merged](matcherResult.createdTime)
              ),
              work2.id -> Left(
                work2.transition[Merged](matcherResult.createdTime)
              ),
              work3.id -> Left(
                work3.transition[Merged](matcherResult.createdTime)
              )
            )
        }
    }
  }

  it("sends InvisibleWorks unmerged") {
    val work = identifiedWork().invisible()
    val matcherResult = createMatcherResultWith(Set(Set(work)))

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
          messageId = randomUUID.toString,
          message = matcherResult
        ))
        identifiedIndex.index ++= Map(work.id -> work)

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender) should contain only work.id

            mergedIndex shouldBe Map(
              work.id -> Left(
                work.transition[Merged](matcherResult.createdTime)
              )
            )
        }
    }
  }

  it("fails if the matcher result refers to a non-existent work") {
    val work = identifiedWork()
    val matcherResult = createMatcherResultWith(Set(Set(work)))

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
          messageId = randomUUID.toString,
          message = matcherResult
        ))

        whenReady(mergerSQSLambda.processMessages(messages).failed) {
          exc =>
            getWorksSent(workSender) shouldBe empty
            exc shouldBe a[java.lang.RuntimeException]
            exc.getMessage shouldBe s"Works not found: Map(${work.id} -> weco.pipeline_storage.RetrieverNotFoundException: Nothing found with ID ${work.id}!)"
        }
    }
  }

  it("sends a Work even if the matched version is not the latest") {
    val workA = identifiedWork()
    val workB = identifiedWork()
    val newerWorkB =
      identifiedWork(canonicalId = workB.state.canonicalId)
        .withVersion(workB.version + 1)
    val matcherResult = createMatcherResultWith(Set(Set(workA, workB)))

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
            messageId = randomUUID.toString,
            message = matcherResult
          ))

        identifiedIndex.index ++= Map(workA.id -> workA, workB.id -> newerWorkB)

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender) should contain allOf (workA.id, workB.id)

            mergedIndex shouldBe Map(
              workA.id -> Left(
                workA.transition[Merged](matcherResult.createdTime)
              ),
              workB.id -> Left(
                newerWorkB.transition[Merged](matcherResult.createdTime)
              )
            )
        }
    }
  }

  it("if it merges two Works, it sends two onward results (one merged, one redirected)") {
    val (digitisedWork, physicalWork) = sierraIdentifiedWorkPair()
    val works = List(physicalWork, digitisedWork)
    val matcherResult = createMatcherResultWith(Set(works.toSet))

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
            messageId = randomUUID.toString,
            message = matcherResult
          ))

        identifiedIndex.index ++= Map(
          physicalWork.id -> physicalWork,
          digitisedWork.id -> digitisedWork
        )

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender) should have size 2
            mergedIndex should have size 2

            val redirectedWorks = mergedIndex.collect {
              case (_, Left(work: Work.Redirected[Merged])) => work
            }
            val mergedWorks = mergedIndex.collect {
              case (_, Left(work: Work.Visible[Merged])) => work
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
    val matcherResult = createMatcherResultWith(Set(works.toSet))

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, imageSender) =>
        val messages = List(SQSLambdaMessage(
            messageId = randomUUID.toString,
            message = matcherResult
          ))
        identifiedIndex.index ++= Map(
          physicalWork.id -> physicalWork,
          digitisedWork.id -> digitisedWork,
          miroWork.id -> miroWork
        )

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender).distinct should have size 3
            mergedIndex should have size 4

            val imagesSent = getImagesSent(imageSender).distinct
            imagesSent should have size 1

            val redirectedWorks = mergedIndex.collect {
              case (_, Left(work: Work.Redirected[Merged])) => work
            }
            val mergedWorks = mergedIndex.collect {
              case (_, Left(work: Work.Visible[Merged])) => work
            }
            val images = mergedIndex.collect {
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
    val matcherResult = createMatcherResultWith(
      Set(
        workPair1.toSet,
        workPair2.toSet
      )
    )

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
          messageId = randomUUID.toString,
          message = matcherResult
        ))
        identifiedIndex.index ++= Map(
          physicalWork1.id -> physicalWork1,
          digitisedWork1.id -> digitisedWork1,
          physicalWork2.id -> physicalWork2,
          digitisedWork2.id -> digitisedWork2
        )

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender) should have size 4

            val redirectedWorks = mergedIndex.collect {
              case (_, Left(work: Work.Redirected[Merged])) => work
            }
            val mergedWorks = mergedIndex.collect {
              case (_, Left(work: Work.Visible[Merged])) => work
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
    val matcherResult = createMatcherResultWith(
      Set(Set(visibleWork, deletedWork))
    )

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
          messageId = randomUUID.toString,
          message = matcherResult
        ))
        identifiedIndex.index ++= Map(
          visibleWork.id -> visibleWork,
          deletedWork.id -> deletedWork
        )

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender) should have size 2

            val visibleWorks = mergedIndex.collect {
              case (_, Left(work: Work.Visible[Merged])) => work
            }
            val deletedWorks = mergedIndex.collect {
              case (_, Left(work: Work.Deleted[Merged])) => work
            }

            visibleWorks.map { _.id } shouldBe Seq(visibleWork.id)
            deletedWorks.map { _.id } shouldBe Seq(deletedWork.id)
        }
    }
  }

  it("sends messages even if the works are outdated") {
    val work0 = identifiedWork().withVersion(0)
    val work1 = identifiedWork(canonicalId = work0.state.canonicalId)
      .withVersion(1)
    val matcherResult = createMatcherResultWith(Set(Set(work0)))

    withMergerSQSLambdaFixtures {
      case(mergerSQSLambda, identifiedIndex, mergedIndex, workSender, _, _, _) =>
        val messages = List(SQSLambdaMessage(
          messageId = randomUUID.toString,
          message = matcherResult
        ))
        identifiedIndex.index ++= Map(work1.id -> work1)

        whenReady(mergerSQSLambda.processMessages(messages)) {
          _ =>
            getWorksSent(workSender) shouldBe List(work1.id)

            mergedIndex shouldBe Map(
              work1.id -> Left(
                work1.transition[Merged](matcherResult.createdTime)
              )
            )
        }
    }
  }


  def withMergerSQSLambdaFixtures[R](
    testWith: TestWith[
      (
        MergerSQSLambda[DummyConfig],
        MemoryRetriever[Work[WorkState.Identified]],
        mutable.Map[String, WorkOrImage],
        MemoryMessageSender,
        MemoryMessageSender,
        MemoryMessageSender,
        MemoryMessageSender,
      ),
      R
    ]
  ): R = {
    val identifiedIndex: MemoryRetriever[Work[WorkState.Identified]] =
      new MemoryRetriever[Work[WorkState.Identified]]()
    val mergedIndex = mutable.Map[String, WorkOrImage]()
    val workSender = new MemoryMessageSender()
    val pathSender = new MemoryMessageSender()
    val pathconcatSender = new MemoryMessageSender()
    val imageSender = new MemoryMessageSender()

    withMergerSQSLambda(
      identifiedIndex,
      mergedIndex,
      workSender,
      pathSender,
      pathconcatSender,
      imageSender,
    ) {
      mergerSQSLambda =>
        testWith((
          mergerSQSLambda,
          identifiedIndex,
          mergedIndex,
          workSender,
          pathSender,
          pathconcatSender,
          imageSender,
        ))
    }
  }
}

