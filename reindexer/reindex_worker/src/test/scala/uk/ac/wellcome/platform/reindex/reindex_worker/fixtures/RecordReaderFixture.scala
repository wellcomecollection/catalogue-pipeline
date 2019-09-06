package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import uk.ac.wellcome.platform.reindex.reindex_worker.services.RecordReader

trait RecordReaderFixture extends ReindexDynamoFixtures {
  def createRecordReader: RecordReader =
    new RecordReader(
      maxRecordsScanner = createMaxRecordsScanner,
      parallelScanner = createParallelScanner
    )
}
