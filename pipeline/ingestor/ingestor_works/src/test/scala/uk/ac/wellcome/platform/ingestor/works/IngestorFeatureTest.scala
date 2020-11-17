package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.DerivedWorkIndexConfig
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.ingestor.common.fixtures.IngestorFixtures
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.models.Implicits._
import WorkState.{Derived, Identified}
import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.platform.ingestor.common.services.IngestorWorkerService

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
        withWorkIngestorWorkerService(queue, index) { _ =>
          assertElasticsearchEventuallyHasWork[Derived](
            index,
            WorkTransformer.deriveData(work))
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
        withWorkIngestorWorkerService(queue, index) { _ =>
          assertElasticsearchEventuallyHasWork[Derived](
            index,
            WorkTransformer.deriveData(work))
        }
      }
    }
  }

  def withWorkIngestorWorkerService[R](queue: Queue, index: Index)(
    testWith: TestWith[IngestorWorkerService[Work[Identified], Work[Derived]],
                       R]): R =
    withWorkerService(
      queue,
      new ElasticIndexer[Work[Derived]](
        elasticClient,
        index,
        DerivedWorkIndexConfig),
      WorkTransformer.deriveData
    )(testWith)
}
