package weco.pipeline.ingestor.works

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.utils.JsonAssertions
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.IndexFixtures
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.ingestor.works.fixtures.WorksIngestorFixtures

class IngestorFeatureTest
    extends AnyFunSpec
    with Matchers
    with JsonAssertions
    with IndexFixtures
    with WorksIngestorFixtures
    with WorkGenerators {

  it("ingests a Miro work") {
    val work = denormalisedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )

    withLocalWorksIndex { indexedIndex =>
      withLocalDenormalisedWorksIndex { denormalisedIndex =>
        insertIntoElasticsearch(denormalisedIndex, work)
        withLocalSqsQueue() { queue =>
          withWorkIngestorWorkerService(queue, denormalisedIndex, indexedIndex) {
            _ =>
              sendNotificationToSQS(queue = queue, body = work.id)
              assertWorkIndexed(indexedIndex, work)
          }
        }
      }
    }
  }

  it("ingests a Sierra work") {
    val work = denormalisedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    withLocalWorksIndex { indexedIndex =>
      withLocalDenormalisedWorksIndex { denormalisedIndex =>
        insertIntoElasticsearch(denormalisedIndex, work)
        withLocalSqsQueue() { queue =>
          withWorkIngestorWorkerService(queue, denormalisedIndex, indexedIndex) {
            _ =>
              sendNotificationToSQS(queue = queue, body = work.id)
              assertWorkIndexed(indexedIndex, work)
          }
        }
      }
    }
  }
}
