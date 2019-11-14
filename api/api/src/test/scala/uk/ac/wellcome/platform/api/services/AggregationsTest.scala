package uk.ac.wellcome.platform.api.services

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import com.sksamuel.elastic4s.Index

import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.WorksGenerators

class AggregationsTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with ElasticsearchFixtures
    with WorksGenerators {

  val worksService = new WorksService(
    searchService = new ElasticsearchService(elasticClient)
  )

  it("returns more than 10 workType aggregations") {
    val workTypes = List(
      WorkType("a", "Books"),
      WorkType("q", "Digital Images"),
      WorkType("x", "E-manuscripts, Asian"),
      WorkType("l", "Ephemera"),
      WorkType("e", "Maps"),
      WorkType("k", "Pictures"),
      WorkType("w", "Student dissertations"),
      WorkType("r", "3-D Objects"),
      WorkType("m", "CD-Roms"),
      WorkType("v", "E-books"),
      WorkType("s", "E-sound"),
      WorkType("d", "Journals"),
      WorkType("p", "Mixed materials"),
      WorkType("i", "Sound"),
      WorkType("g", "Videorecordings"),
      WorkType("h", "Archives and manuscripts"),
      WorkType("n", "Cinefilm"),
      WorkType("j", "E-journals"),
      WorkType("f", "E-videos"),
      WorkType("b", "Manuscripts, Asian"),
      WorkType("c", "Music"),
      WorkType("u", "Standing order"),
      WorkType("z", "Web sites"),
    )
    val works = workTypes.flatMap { workType =>
      (0 to 4).map(_ => createIdentifiedWorkWith(workType = Some(workType)))
    }
    withLocalWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)
      val searchOptions = WorksSearchOptions(
        aggregations = List(AggregationRequest.WorkType)
      )
      whenReady(aggregationQuery(index, searchOptions)) { aggs =>
        aggs.workType should not be empty
        val buckets = aggs.workType.get.buckets
        buckets.length shouldBe workTypes.length
        buckets.map(_.data.label) should contain theSameElementsAs workTypes
          .map(_.label)
      }
    }
  }

  it("aggregate over filtered dates, using only 'from' date") {
    val works = List(
      createDatedWork("1850"),
      createDatedWork("1850-2000"),
      createDatedWork("1860-1960"),
      createDatedWork("1960"),
      createDatedWork("1960-1964"),
      createDatedWork("1962"),
    )
    withLocalWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)
      val searchOptions = WorksSearchOptions(
        aggregations = List(AggregationRequest.ProductionDate),
        filters = List(
          DateRangeFilter(Some(LocalDate.of(1960, 1, 1)), None)
        )
      )
      whenReady(aggregationQuery(index, searchOptions)) { aggs =>
        aggs.productionDates shouldBe Some(
          Aggregation(
            List(
              AggregationBucket(Period("1960"), 2),
              AggregationBucket(Period("1962"), 1)
            )
          )
        )
      }
    }
  }

  private def aggregationQuery(index: Index,
                               searchOptions: WorksSearchOptions) =
    worksService
      .listWorks(index, searchOptions)
      .map(_.right.get.aggregations.get)
}
