package uk.ac.wellcome.platform.api.services

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import com.sksamuel.elastic4s.Index

import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.display.models.{AggregationRequest, SortingOrder}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.WorkType

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
      whenReady(aggregationQuery(index, AggregationRequest.WorkType)) { aggs =>
        aggs.workType should not be empty
        val buckets = aggs.workType.get.buckets
        buckets.length shouldBe workTypes.length
        buckets.map(_.data.label) should contain theSameElementsAs workTypes
          .map(_.label)
      }
    }
  }

  private def aggregationQuery(index: Index,
                               aggregations: AggregationRequest*) = {
    val searchOptions = WorksSearchOptions(
      filters = Nil,
      pageSize = 10,
      pageNumber = 1,
      aggregations = aggregations.toList,
      sortBy = Nil,
      sortOrder = SortingOrder.Ascending
    )
    worksService
      .listWorks(index, searchOptions)
      .map(_.right.get.aggregations.get)
  }
}
