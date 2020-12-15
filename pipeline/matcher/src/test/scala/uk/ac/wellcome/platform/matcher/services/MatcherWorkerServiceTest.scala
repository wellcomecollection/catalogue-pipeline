package uk.ac.wellcome.platform.matcher.services

import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import uk.ac.wellcome.models.work.generators.SierraWorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified
import uk.ac.wellcome.pipeline_storage.MemoryRetriever

import scala.concurrent.ExecutionContext.Implicits.global

class MatcherWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with SierraWorkGenerators {

  private val identifierA = IdState.Identified(
    createCanonicalId,
    createSierraSystemSourceIdentifierWith(value = "A"))
  private val identifierB = IdState.Identified(
    createCanonicalId,
    createSierraSystemSourceIdentifierWith(value = "B"))
  private val identifierC = IdState.Identified(
    createCanonicalId,
    createSierraSystemSourceIdentifierWith(value = "C"))

  it("creates a work without identifiers") {
    // Work Av1 created without any matched works
    val updatedWork = sierraIdentifiedWork()
    val expectedMatchedWorks =
      MatcherResult(
        Set(
          MatchedIdentifiers(identifiers = Set(WorkIdentifier(updatedWork)))
        )
      )

    implicit val retriever: MemoryRetriever[Work[Identified]] =
      new MemoryRetriever[Work[Identified]]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(
          updatedWork,
          expectedResult = expectedMatchedWorks)
      }
    }
  }

  it(
    "sends an invisible work as a single matched result with no other matched identifiers") {
    val invisibleWork = identifiedWork().invisible()
    val expectedMatchedWorks =
      MatcherResult(
        Set(
          MatchedIdentifiers(identifiers = Set(WorkIdentifier(invisibleWork)))
        )
      )

    implicit val retriever: MemoryRetriever[Work[Identified]] =
      new MemoryRetriever[Work[Identified]]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(
          invisibleWork,
          expectedResult = expectedMatchedWorks)
      }
    }
  }

  it(
    "work A with one link to B and no existing works returns a single matched work") {
    // Work Av1
    val workAv1 =
      identifiedWork(
        sourceIdentifier = identifierA.sourceIdentifier,
        canonicalId = identifierA.canonicalId)
        .withVersion(1)
        .mergeCandidates(List(MergeCandidate(identifierB)))

    // Work Av1 matched to B (before B exists hence version is None)
    // need to match to works that do not exist to support
    // bi-directionally matched works without deadlocking (A->B, B->A)
    val expectedMatchedWorks = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(identifierA.canonicalId, version = Some(1)),
            WorkIdentifier(identifierB.canonicalId, version = None)
          )
        )
      )
    )

    implicit val retriever: MemoryRetriever[Work[Identified]] =
      new MemoryRetriever[Work[Identified]]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(
          workAv1,
          expectedResult = expectedMatchedWorks)
      }
    }
  }

  it(
    "matches a work with one link then matches the combined work to a new work") {
    // Work Av1
    val workAv1 = identifiedWork(
      sourceIdentifier = identifierA.sourceIdentifier,
      canonicalId = identifierA.canonicalId).withVersion(1)

    val expectedMatchedWorksAv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 1))
        )
      )
    )

    // Work Bv1
    val workBv1 = identifiedWork(
      sourceIdentifier = identifierB.sourceIdentifier,
      canonicalId = identifierB.canonicalId).withVersion(1)

    val expectedMatchedWorksBv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierB.canonicalId, version = 1))
        )
      )
    )

    // Work Av1 matched to B
    val workAv2 = identifiedWork(
      sourceIdentifier = identifierA.sourceIdentifier,
      canonicalId = identifierA.canonicalId)
      .withVersion(2)
      .mergeCandidates(List(MergeCandidate(identifierB)))

    val expectedMatchedWorksAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(identifierA.canonicalId, version = 2),
            WorkIdentifier(identifierB.canonicalId, version = 1)
          )
        )
      )
    )

    // Work Cv1
    val workCv1 = identifiedWork(
      sourceIdentifier = identifierC.sourceIdentifier,
      canonicalId = identifierC.canonicalId)
      .withVersion(1)

    val expectedMatcherWorksCv1 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier(identifierC.canonicalId, version = 1))
          )
        )
      )

    // Work Bv2 matched to C
    val workBv2 = identifiedWork(
      sourceIdentifier = identifierB.sourceIdentifier,
      canonicalId = identifierB.canonicalId)
      .withVersion(2)
      .mergeCandidates(List(MergeCandidate(identifierC)))

    val expectedMatchedWorksBv2 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers = Set(
              WorkIdentifier(identifierA.canonicalId, version = 2),
              WorkIdentifier(identifierB.canonicalId, version = 2),
              WorkIdentifier(identifierC.canonicalId, version = 1)
            )
          )
        )
      )

    implicit val retriever: MemoryRetriever[Work[Identified]] =
      new MemoryRetriever[Work[Identified]]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workAv1, expectedMatchedWorksAv1)
        processAndAssertMatchedWorkIs(workBv1, expectedMatchedWorksBv1)
        processAndAssertMatchedWorkIs(workAv2, expectedMatchedWorksAv2)
        processAndAssertMatchedWorkIs(workCv1, expectedMatcherWorksCv1)
        processAndAssertMatchedWorkIs(workBv2, expectedMatchedWorksBv2)
      }
    }
  }

  it("breaks matched works into individual works") {
    // Work Av1
    val workAv1 = identifiedWork(
      sourceIdentifier = identifierA.sourceIdentifier,
      canonicalId = identifierA.canonicalId).withVersion(1)

    val expectedMatchedWorksAv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 1))
        )
      )
    )

    // Work Bv1
    val workBv1 = identifiedWork(
      sourceIdentifier = identifierB.sourceIdentifier,
      canonicalId = identifierB.canonicalId).withVersion(1)

    val expectedMatchedWorksBv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierB.canonicalId, version = 1))
        )
      )
    )

    // Match Work A to Work B
    val workAv2MatchedToB =
      identifiedWork(
        sourceIdentifier = identifierA.sourceIdentifier,
        canonicalId = identifierA.canonicalId)
        .withVersion(2)
        .mergeCandidates(List(MergeCandidate(identifierB)))

    val expectedMatchedWorksAv2MatchedToB =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers = Set(
              WorkIdentifier(identifierA.canonicalId, version = 2),
              WorkIdentifier(identifierB.canonicalId, version = 1)
            )
          )
        )
      )

    // A no longer matches B
    val workAv3WithNoMatchingWorks =
      identifiedWork(
        sourceIdentifier = identifierA.sourceIdentifier,
        canonicalId = identifierA.canonicalId).withVersion(3)

    val expectedMatchedWorksAv3 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier(identifierA.canonicalId, version = 3))
          ),
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier(identifierB.canonicalId, version = 1))
          )
        )
      )

    implicit val retriever: MemoryRetriever[Work[Identified]] =
      new MemoryRetriever[Work[Identified]]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workAv1, expectedMatchedWorksAv1)
        processAndAssertMatchedWorkIs(workBv1, expectedMatchedWorksBv1)
        processAndAssertMatchedWorkIs(
          workAv2MatchedToB,
          expectedMatchedWorksAv2MatchedToB)
        processAndAssertMatchedWorkIs(
          workAv3WithNoMatchingWorks,
          expectedMatchedWorksAv3)
      }
    }
  }

  it("does not match a lower version") {
    val workAv2 = identifiedWork(
      sourceIdentifier = identifierA.sourceIdentifier,
      canonicalId = identifierA.canonicalId).withVersion(2)

    val expectedMatchedWorkAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 2))
        )
      )
    )

    implicit val retriever: MemoryRetriever[Work[Identified]] =
      new MemoryRetriever[Work[Identified]]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withWorkerService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workAv2, expectedMatchedWorkAv2)

          // Work V1 is sent but not matched
          val workAv1 =
            identifiedWork(
              sourceIdentifier = identifierA.sourceIdentifier,
              canonicalId = identifierA.canonicalId).withVersion(1)

          sendWork(workAv1, retriever, queue)
          eventually {
            noMessagesAreWaitingIn(queue)
            noMessagesAreWaitingIn(dlq)

            messageSender
              .getMessages[MatcherResult]
              .last shouldBe expectedMatchedWorkAv2
          }
        }
    }
  }

  it("does not match an existing version with different information") {
    val workAv2 = identifiedWork(
      sourceIdentifier = identifierA.sourceIdentifier,
      canonicalId = identifierA.canonicalId).withVersion(2)

    val expectedMatchedWorkAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 2))
        )
      )
    )

    implicit val retriever: MemoryRetriever[Work[Identified]] =
      new MemoryRetriever[Work[Identified]]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withWorkerService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workAv2, expectedMatchedWorkAv2)

          // Work V1 is sent but not matched
          val differentWorkAv2 =
            identifiedWork(
              sourceIdentifier = identifierA.sourceIdentifier,
              canonicalId = identifierA.canonicalId)
              .withVersion(2)
              .mergeCandidates(List(MergeCandidate(identifierB)))

          sendWork(differentWorkAv2, retriever, queue)
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }

  private def processAndAssertMatchedWorkIs(workToMatch: Work[Identified],
                                            expectedResult: MatcherResult)(
    implicit
    retriever: MemoryRetriever[Work[Identified]],
    queue: SQS.Queue,
    messageSender: MemoryMessageSender): Assertion = {
    sendWork(workToMatch, retriever, queue)
    eventually {
      messageSender.getMessages[MatcherResult].last shouldBe expectedResult
    }
  }
}
