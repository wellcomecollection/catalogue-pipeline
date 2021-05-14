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
import uk.ac.wellcome.pipeline_storage.MemoryRetriever
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.generators.WorkLinksGenerators
import uk.ac.wellcome.platform.matcher.models.WorkLinks
import weco.fixtures.TimeAssertions

import scala.concurrent.ExecutionContext.Implicits.global

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorkLinksGenerators
    with TimeAssertions {

  it("processes a message with a single WorkLinks with no linked works") {
    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue(visibilityTimeout = 5) { queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        val links = createWorkLinksWith(referencedIds = Set.empty)

        val expectedWorks =
          Set(
            MatchedIdentifiers(
              identifiers =
                Set(WorkIdentifier(links.workId, version = links.version))
            )
          )

        sendWork(links, retriever, queue)

        eventually {
          messageSender.messages should have size 1

          val result = messageSender.getMessages[MatcherResult].head
          result.works shouldBe expectedWorks
          assertRecent(result.createdTime)
        }
      }
    }
  }

  it("skips a message if the graph store already has a newer version") {
    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkGraphTable { graphTable =>
          withWorkerService(retriever, queue, messageSender, graphTable) { _ =>
            val existingWorkVersion = 2
            val updatedWorkVersion = 1

            val linksV1 = createWorkLinksWith(version = updatedWorkVersion)

            val nodeV2 = WorkNode(
              id = linksV1.workId,
              version = Some(existingWorkVersion),
              linkedIds = Nil,
              componentId = ciHash(linksV1.workId)
            )
            put(dynamoClient, graphTable.name)(nodeV2)

            sendWork(linksV1, retriever, queue)

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
