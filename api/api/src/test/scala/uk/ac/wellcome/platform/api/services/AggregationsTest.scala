package uk.ac.wellcome.platform.api.services

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.index.IndexFixtures
import uk.ac.wellcome.models.work.generators.{
  GenreGenerators,
  ProductionEventGenerators,
  SubjectGenerators,
  WorkGenerators
}
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Format, Period, Subject}

class AggregationsTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IndexFixtures
    with SubjectGenerators
    with GenreGenerators
    with ProductionEventGenerators
    with SearchOptionsGenerators
    with WorkGenerators {

  val worksService = new WorksService(
    searchService = new ElasticsearchService(elasticClient)
  )

  it("returns more than 10 format aggregations") {
    val formats = Format.values
    val works = formats.flatMap { format =>
      (0 to 4).map(_ => indexedWork().format(format))
    }
    withLocalWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)
      val searchOptions = createWorksSearchOptionsWith(
        aggregations = List(WorkAggregationRequest.Format)
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
    val dates = Seq(
      "1850",
      "1850-2000",
      "1860-1960",
      "1960",
      "1960-1964",
      "1962"
    )

    val works = dates.map { dateLabel =>
      indexedWork()
        .production(
          List(createProductionEventWith(dateLabel = Some(dateLabel)))
        )
    }

    withLocalWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)
      val searchOptions = createWorksSearchOptionsWith(
        aggregations = List(WorkAggregationRequest.ProductionDate),
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

  describe("aggregations with filters") {
    val formats = Format.values
    val subjects = (0 to 5).map(_ => createSubject)
    val works = formats.zipWithIndex.map {
      case (format, i) =>
        indexedWork()
          .format(format)
          .subjects(List(subjects(i / subjects.size)))
    }

    it("applies filters to their related aggregations") {
      withLocalWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        val searchOptions = createWorksSearchOptionsWith(
          aggregations =
            List(WorkAggregationRequest.Format, WorkAggregationRequest.Subject),
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
        val searchOptions = createWorksSearchOptionsWith(
          aggregations =
            List(WorkAggregationRequest.Format, WorkAggregationRequest.Subject),
          filters = List(
            FormatFilter(List(Format.Books.id)),
            SubjectFilter(Seq(subjectQuery))
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
        val searchOptions = createWorksSearchOptionsWith(
          aggregations =
            List(WorkAggregationRequest.Format, WorkAggregationRequest.Subject),
          filters = List(
            FormatFilter(List(Format.Books.id)),
            SubjectFilter(Seq(subjectQuery))
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

  private def aggregationQuery(index: Index, searchOptions: WorkSearchOptions) =
    worksService
      .listOrSearchWorks(index, searchOptions)
      .map(_.right.get.aggregations.get)
}
