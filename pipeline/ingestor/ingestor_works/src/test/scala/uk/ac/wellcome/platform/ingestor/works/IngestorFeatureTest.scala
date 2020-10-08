package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.IdentifiedWorkIndexConfig
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.ingestor.common.fixtures.IngestorFixtures
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified

class IngestorFeatureTest
    extends AnyFunSpec
    with Matchers
    with JsonAssertions
    with IngestorFixtures
    with WorkGenerators {

  it("ingests a Miro work") {
    val work = identifiedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )

    withLocalSqsQueue() { queue =>
      sendMessage[Work[Identified]](queue = queue, obj = work)
      withLocalWorksIndex { index =>
        withWorkerService(
          queue,
          index,
          new ElasticIndexer[Work[Identified]](elasticClient, index, IdentifiedWorkIndexConfig)) { _ =>
          assertElasticsearchEventuallyHasWork(index, work)
        }
      }
    }
  }

  it("ingests a Sierra work") {
    val work = identifiedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    withLocalSqsQueue() { queue =>
      sendMessage[Work[Identified]](queue = queue, obj = work)
      withLocalWorksIndex { index =>
        withWorkerService(
          queue,
          index,
          new ElasticIndexer[Work[Identified]](elasticClient, index, IdentifiedWorkIndexConfig)) { _ =>
          assertElasticsearchEventuallyHasWork(index, work)
        }
      }
    }
  }
}
