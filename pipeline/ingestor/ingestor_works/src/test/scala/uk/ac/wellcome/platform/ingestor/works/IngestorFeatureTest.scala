package uk.ac.wellcome.platform.ingestor.works

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.WorksIndexConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork
import uk.ac.wellcome.platform.ingestor.common.fixtures.IngestorFixtures
import uk.ac.wellcome.platform.ingestor.works.services.WorkIndexer

import scala.concurrent.ExecutionContext.Implicits.global

class IngestorFeatureTest
    extends FunSpec
    with Matchers
    with JsonAssertions
    with ScalaFutures
    with IngestorFixtures
    with ElasticsearchFixtures
    with WorksGenerators {

  it("ingests a Miro work") {
    val work = createIdentifiedWork

    withLocalSqsQueue { queue =>
      sendMessage[IdentifiedBaseWork](queue = queue, obj = work)
      withLocalWorksIndex { index =>
        withWorkerService(queue, index, WorksIndexConfig, new WorkIndexer(elasticClient,index)) { _ =>
          assertElasticsearchEventuallyHasWork(index, work)
        }
      }
    }
  }

  it("ingests a Sierra work") {
    val work = createIdentifiedWorkWith(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    withLocalSqsQueue { queue =>
      sendMessage[IdentifiedBaseWork](queue = queue, obj = work)
      withLocalWorksIndex { index =>
        withWorkerService(queue, index, WorksIndexConfig, new WorkIndexer(elasticClient,index)) { _ =>
          assertElasticsearchEventuallyHasWork(index, work)
        }
      }
    }
  }


}
