package uk.ac.wellcome.platform.ingestor.works.fixtures

import com.sksamuel.elastic4s.Index
import org.scalatest.Suite
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.ingestor.common.fixtures.IngestorFixtures
import uk.ac.wellcome.platform.ingestor.works.config.WorksIndexConfig

trait IngestorWorksFixtures extends ElasticsearchFixtures with IngestorFixtures{ this: Suite =>

  def withLocalWorksIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = WorksIndexConfig) { index =>
      testWith(index)
    }
}
