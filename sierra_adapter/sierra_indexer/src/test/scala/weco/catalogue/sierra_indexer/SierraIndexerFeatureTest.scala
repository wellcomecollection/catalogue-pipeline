package weco.catalogue.sierra_indexer

import com.sksamuel.elastic4s.ElasticClient
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.sierra_adapter.generators.SierraGenerators
import weco.catalogue.sierra_adapter.models.SierraTransformable
import weco.catalogue.sierra_indexer.services.Worker

import scala.concurrent.ExecutionContext.Implicits.global

class SierraIndexerFeatureTest extends AnyFunSpec with Matchers with ElasticsearchFixtures with SQS with Akka with SierraGenerators with S3ObjectLocationGenerators with IntegrationPatience {
  def withWorker[R](
    queue: Queue, typedStore: MemoryTypedStore[S3ObjectLocation, SierraTransformable])(
    testWith: TestWith[Worker, R]
  ): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        implicit val client: ElasticClient = elasticClient

        val worker = new Worker(sqsStream, typedStore)

        worker.run()

        testWith(worker)
      }
    }

  it("indexes bib records") {
    val location = createS3ObjectLocation
    val transformable = createSierraTransformableWith(
      maybeBibRecord = Some(
        createSierraBibRecordWith(
          data =
            s"""
               |
               |""".stripMargin
        )
      )
    )
    val store = MemoryTypedStore[S3ObjectLocation, SierraTransformable](
      initialEntries = Map(location -> transformable)
    )

    println(s"BOOM! $store")
    assert(true)
  }
}
