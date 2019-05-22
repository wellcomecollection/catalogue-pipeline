package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import io.circe.Decoder
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{MaxRecordsScanner, ParallelScanner, ScanSpecScanner}

import scala.util.{Success, Try}

trait DynamoFixtures extends ReindexableTable {
  def parseRecords[T](result: Try[Seq[String]])(implicit decoder: Decoder[T]): Seq[T] = {
    result shouldBe a[Success[_]]
    result.get.map { fromJson[T](_).get }
  }

  def withScanSpecScanner[R](testWith: TestWith[ScanSpecScanner, R]): R = {
    val scanner = new ScanSpecScanner(dynamoDbClient)

    testWith(scanner)
  }

  def withParallelScanner[R](testWith: TestWith[ParallelScanner, R]): R =
    withScanSpecScanner { scanSpecScanner =>
      val scanner = new ParallelScanner(scanSpecScanner = scanSpecScanner)

      testWith(scanner)
    }

  def withMaxRecordsScanner[R](testWith: TestWith[MaxRecordsScanner, R]): R =
    withScanSpecScanner { scanSpecScanner =>
      val scanner = new MaxRecordsScanner(scanSpecScanner = scanSpecScanner)

      testWith(scanner)
    }
}
