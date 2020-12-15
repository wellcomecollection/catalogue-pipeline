package uk.ac.wellcome.platform.matcher

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode
}
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.pipeline_storage.MemoryRetriever
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorkGenerators {

  it(
    "processes a message with a simple Work.Visible[Identified] with no linked works") {
    implicit val retriever: MemoryRetriever[Work[Identified]] = createRetriever
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        val work = identifiedWork()

        val expectedResult = MatcherResult(
          Set(
            MatchedIdentifiers(
              identifiers = Set(WorkIdentifier(work))
            )
          )
        )

        sendWork(work, retriever, queue)

        eventually {
          messageSender.getMessages[MatcherResult].distinct shouldBe Seq(
            expectedResult)
        }
      }
    }
  }

  it("skips a message if the graph store already has a newer version") {
    implicit val retriever: MemoryRetriever[Work[Identified]] = createRetriever
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkGraphTable { graphTable =>
          withWorkerService(retriever, queue, messageSender, graphTable) { _ =>
            val existingWorkVersion = 2
            val updatedWorkVersion = 1

            val workAv1 = identifiedWork().withVersion(updatedWorkVersion)

            val existingWorkAv2 = WorkNode(
              id = workAv1.state.canonicalId,
              version = Some(existingWorkVersion),
              linkedIds = Nil,
              componentId = workAv1.state.canonicalId
            )
            put(dynamoClient, graphTable.name)(existingWorkAv2)

            sendWork(workAv1, retriever, queue)

            eventually {
              noMessagesAreWaitingIn(queue)
              noMessagesAreWaitingIn(dlq)
            }

            messageSender.messages shouldBe empty
          }
        }
    }
  }
}
