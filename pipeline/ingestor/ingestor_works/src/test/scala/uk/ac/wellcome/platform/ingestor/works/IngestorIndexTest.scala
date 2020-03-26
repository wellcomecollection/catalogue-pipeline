package uk.ac.wellcome.platform.ingestor.works

import com.sksamuel.elastic4s.Index
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.platform.ingestor.common.fixtures.IngestorFixtures

class IngestorIndexTest
    extends FunSpec
    with SQS
    with Matchers
    with ScalaFutures
    with BigMessagingFixture
    with ElasticsearchFixtures
    with IngestorFixtures {

  it("creates the index at startup if it doesn't already exist") {
    val index = Index("works")

    eventuallyDeleteIndex(index)

    withLocalSqsQueue { queue =>
      withWorkerService(queue, index) { _ =>
        eventuallyIndexExists(index)
      }
    }
  }
}
