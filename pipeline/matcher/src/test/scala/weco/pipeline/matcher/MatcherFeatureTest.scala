package weco.pipeline.matcher

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.index.IndexFixtures
import weco.catalogue.internal_model.work.DeletedReason.SuppressedFromSource
import weco.catalogue.internal_model.work.MergeCandidate
import weco.catalogue.internal_model.work.generators.SourceWorkGenerators
import weco.catalogue.internal_model.Implicits._
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.MatcherResult._
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode,
  WorkStub
}
import weco.pipeline.matcher.storage.elastic.ElasticWorkStubRetriever
import weco.pipeline_storage.Retriever
import weco.pipeline_storage.memory.MemoryRetriever

import scala.concurrent.ExecutionContext.Implicits.global

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with IndexFixtures
    with WorkStubGenerators
    with SourceWorkGenerators
    with TimeAssertions {

  it("processes a single Work with nothing linked to it") {
    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        val work = createWorkWith(referencedWorkIds = Set.empty)

        val expectedWorks =
          Set(
            MatchedIdentifiers(
              identifiers = Set(WorkIdentifier(work.id, version = work.version))
            )
          )

        sendWork(work, retriever, queue)

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
    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkGraphTable { graphTable =>
          withWorkerService(retriever, queue, messageSender, graphTable) { _ =>
            val existingWorkVersion = 2
            val updatedWorkVersion = 1

            val workV1 = createWorkWith(version = updatedWorkVersion)

            val nodeV2 = WorkNode(
              id = workV1.id,
              version = Some(existingWorkVersion),
              linkedIds = Nil,
              componentId = ciHash(workV1.id)
            )

            putTableItem(nodeV2, table = graphTable)

            sendWork(workV1, retriever, queue)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }

            messageSender.messages shouldBe empty
          }
        }
    }
  }

  it("doesn't match through a suppressed Sierra e-bib") {
    // This test covers the case where we have three works which are notionally
    // connected:
    //
    //    (Sierra physical bib)
    //              |
    //    (Sierra digitised bib)
    //              |
    //    (Digitised METS record)
    //
    // If the digitised bib is suppressed in Sierra, we won't be able to create a
    // IIIF Presentation manifest or display a digitised item.  We shouldn't match
    // through the digitised bib.  They should be returned as three distinct works.
    //
    val sierraPhysicalBib = sierraPhysicalIdentifiedWork()

    val sierraDigitisedBib = sierraDigitalIdentifiedWork()
      .mergeCandidates(
        List(
          MergeCandidate(
            id = IdState.Identified(
              canonicalId = sierraPhysicalBib.state.canonicalId,
              sourceIdentifier = sierraPhysicalBib.state.sourceIdentifier
            ),
            reason = "Physical/digitised Sierra work"
          )
        )
      )
      .deleted(SuppressedFromSource("Sierra"))

    val metsRecord = metsIdentifiedWork()
      .mergeCandidates(
        List(
          MergeCandidate(
            id = IdState.Identified(
              canonicalId = sierraDigitisedBib.state.canonicalId,
              sourceIdentifier = sierraDigitisedBib.state.sourceIdentifier
            ),
            reason = "METS work"
          )
        )
      )

    val works = Seq(sierraPhysicalBib, sierraDigitisedBib, metsRecord)

    withLocalIdentifiedWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)

      implicit val retriever: Retriever[WorkStub] =
        new ElasticWorkStubRetriever(elasticClient, index)

      val messageSender = new MemoryMessageSender()

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerService(retriever, queue, messageSender) { _ =>
            works.foreach { w =>
              sendNotificationToSQS(queue, body = w.id)
            }

            eventually {
              val results = messageSender.getMessages[MatcherResult]
              results should have size 3

              // Every collection of MatchedIdentifiers only has a single entry.
              //
              // This is a bit easier than matching directly on the result, which varies
              // depending on the exact order the notifications are processed.
              results.forall { matcherResult =>
                matcherResult.works.forall(_.identifiers.size == 1)
              }

              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
      }
    }
  }
}
