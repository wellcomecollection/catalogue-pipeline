package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.aggs.{
  AbstractAggregation,
  Aggregation,
  FilterAggregation
}
import com.sksamuel.elastic4s.requests.searches.queries.{BoolQuery, Query}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.display.models.AggregationRequest
import uk.ac.wellcome.platform.api.models.{
  GenreFilter,
  IdentifiedWorkFilter,
  LanguageFilter,
  WorkFilter,
  WorkTypeFilter
}

class FilteredAggregationBuilderTest extends FunSpec with Matchers {

  describe("filter discrimination") {
    it("separates independent and aggregation-dependent filters") {
      val workTypeFilter = WorkTypeFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FilteredAggregationBuilder(
        List(AggregationRequest.WorkType, AggregationRequest.License),
        List(workTypeFilter, languageFilter, IdentifiedWorkFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.aggregationDependentFilters should contain only workTypeFilter
      sut.independentFilters should contain only (languageFilter, IdentifiedWorkFilter)
    }

    it("handles the case where all filters are independent") {
      val workTypeFilter = WorkTypeFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FilteredAggregationBuilder(
        List(AggregationRequest.License),
        List(workTypeFilter, languageFilter, IdentifiedWorkFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.aggregationDependentFilters should have length 0
      sut.independentFilters should contain only (languageFilter, workTypeFilter, IdentifiedWorkFilter)
    }

    it("handles the case where all filters are aggregation-dependent") {
      val workTypeFilter = WorkTypeFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FilteredAggregationBuilder(
        List(AggregationRequest.WorkType, AggregationRequest.Language),
        List(workTypeFilter, languageFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.aggregationDependentFilters should contain only (workTypeFilter, languageFilter)
      sut.independentFilters should have length 0
    }
  }

  describe("aggregation-level filtering") {
    it("applies to aggregations with a paired filter") {
      val workTypeFilter = WorkTypeFilter(Seq("bananas"))
      val sut = new FilteredAggregationBuilder(
        List(AggregationRequest.WorkType),
        List(workTypeFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.aggregations should have length 1
      sut.aggregations.head shouldBe a[FilterAggregation]
      sut.aggregations.head.subaggs.head shouldBe a[MockAggregation]
      sut.aggregations.head.subaggs.head
        .asInstanceOf[MockAggregation]
        .request shouldBe AggregationRequest.WorkType
    }

    it("does not apply to aggregations without a paired filter") {
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FilteredAggregationBuilder(
        List(AggregationRequest.WorkType),
        List(languageFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.aggregations should have length 1
      sut.aggregations.head should not be a[FilterAggregation]
      sut.aggregations.head shouldBe a[MockAggregation]
      sut.aggregations.head
        .asInstanceOf[MockAggregation]
        .request shouldBe AggregationRequest.WorkType
    }

    it("applies all other aggregation-dependent filters to the paired filter") {
      val workTypeFilter = WorkTypeFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val genreFilter = GenreFilter("durian")
      val sut = new FilteredAggregationBuilder(
        List(
          AggregationRequest.WorkType,
          AggregationRequest.Language,
          AggregationRequest.Genre),
        List(workTypeFilter, languageFilter, genreFilter),
        requestToAggregation,
        filterToQuery
      )

      val agg = sut.aggregations.head.asInstanceOf[FilterAggregation]
      agg.query shouldBe a[BoolQuery]
      val query = agg.query.asInstanceOf[BoolQuery]
      query.filters should not contain MockQuery(workTypeFilter)
      query.filters should contain only (MockQuery(languageFilter), MockQuery(
        genreFilter))
    }
  }

  private def requestToAggregation(request: AggregationRequest): Aggregation =
    MockAggregation("cabbage", request)

  private def filterToQuery(filter: WorkFilter): Query = MockQuery(filter)

  private case class MockQuery(filter: WorkFilter) extends Query

  private case class MockAggregation(name: String,
                                     request: AggregationRequest,
                                     subaggs: Seq[AbstractAggregation] = Nil,
                                     metadata: Map[String, AnyRef] = Map.empty)
      extends Aggregation {
    type T = MockAggregation
    override def subAggregations(aggs: Iterable[AbstractAggregation]): T =
      copy(subaggs = aggs.toSeq)
    override def metadata(map: Map[String, AnyRef]): T = copy(metadata = map)
  }
}
