package uk.ac.wellcome.platform.api.services

import java.time.LocalDate

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.{
  GenreGenerators,
  SubjectGenerators,
  WorksGenerators
}

class AggregationsTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with ElasticsearchFixtures
    with SubjectGenerators
    with GenreGenerators
    with WorksGenerators {

  val worksService = new WorksService(
    searchService = new ElasticsearchService(elasticClient)
  )

  it("returns more than 10 format aggregations") {
    val formats = Format.values
    val works = formats.flatMap { format =>
      (0 to 4).map(_ => createIdentifiedWorkWith(format = Some(format)))
    }
    withLocalWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)
      val searchOptions = SearchOptions(
        aggregations = List(AggregationRequest.Format)
      )
      whenReady(aggregationQuery(index, searchOptions)) { aggs =>
        aggs.format should not be empty
        val buckets = aggs.format.get.buckets
        buckets.length shouldBe formats.length
        buckets.map(_.data.label) should contain theSameElementsAs formats
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
      val searchOptions = SearchOptions(
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

  it("returns empty buckets if they exist") {
    val formats = Format.values
    val works = formats.flatMap { format =>
      (0 to 4).map(_ => createIdentifiedWorkWith(format = Some(format)))
    }
    withLocalWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)
      val searchOptions = SearchOptions(
        searchQuery = Some(SearchQuery("anything will give zero results")),
        aggregations = List(AggregationRequest.Format)
      )
      whenReady(aggregationQuery(index, searchOptions)) { aggs =>
        aggs.format should not be empty
        val buckets = aggs.format.get.buckets
        buckets.length shouldBe formats.length
        buckets.map(_.data.label) should contain theSameElementsAs formats
          .map(_.label)
        buckets.map(_.count) should contain only 0
      }
    }
  }

  describe("aggregations with filters") {
    val formats = Format.values
    val subjects = (0 to 5).map(_ => createSubject)
    val works = formats.zipWithIndex.map {
      case (format, i) =>
        createIdentifiedWorkWith(
          format = Some(format),
          subjects = List(subjects(i / subjects.size))
        )
    }

    it("applies filters to their related aggregations") {
      withLocalWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        val searchOptions = SearchOptions(
          aggregations =
            List(AggregationRequest.Format, AggregationRequest.Subject),
          filters = List(
            FormatFilter(List(Format.Books.id)),
          )
        )
        whenReady(aggregationQuery(index, searchOptions)) { aggs =>
          aggs.format should not be empty
          val buckets = aggs.format.get.buckets
          buckets.length shouldBe formats.length
          buckets.map(_.data) should contain theSameElementsAs formats
        }
      }
    }

    it("applies all non-related filters to aggregations") {
      withLocalWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        val subjectQuery = subjects.head match {
          case Subject(IdState.Unidentifiable, label, _) => label
          case _                                         => "bilberry"
        }
        val searchOptions = SearchOptions(
          aggregations =
            List(AggregationRequest.Format, AggregationRequest.Subject),
          filters = List(
            FormatFilter(List(Format.Books.id)),
            SubjectFilter(subjectQuery)
          )
        )
        whenReady(aggregationQuery(index, searchOptions)) { aggs =>
          val buckets = aggs.format.get.buckets
          val expectedFormats = works.map { _.data.format.get }
          buckets.length shouldBe expectedFormats.length
          buckets.map(_.data) should contain theSameElementsAs expectedFormats
        }
      }
    }

    it("applies all filters to the results") {
      withLocalWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        val subjectQuery = subjects.head match {
          case Subject(IdState.Unidentifiable, label, _) => label
          case _                                         => "passionfruit"
        }
        val searchOptions = SearchOptions(
          aggregations =
            List(AggregationRequest.Format, AggregationRequest.Subject),
          filters = List(
            FormatFilter(List(Format.Books.id)),
            SubjectFilter(subjectQuery)
          )
        )
        whenReady(worksService.listOrSearchWorks(index, searchOptions)) { res =>
          val results = res.right.get.results
          results.map(_.data.format.get) should contain only Format.Books
          results.map(_.data.subjects.head.label) should contain only subjectQuery
        }
      }
    }
  }

  private def aggregationQuery(index: Index, searchOptions: SearchOptions) =
    worksService
      .listOrSearchWorks(index, searchOptions)
      .map(_.right.get.aggregations.get)
}
