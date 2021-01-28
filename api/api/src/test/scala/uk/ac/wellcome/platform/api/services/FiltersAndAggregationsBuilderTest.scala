package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.aggs.{
  AbstractAggregation,
  Aggregation,
  FilterAggregation
}
import com.sksamuel.elastic4s.requests.searches.queries.{BoolQuery, Query}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.display.models.WorkAggregationRequest
import uk.ac.wellcome.platform.api.models._

class FiltersAndAggregationsBuilderTest extends AnyFunSpec with Matchers {

  describe("filter discrimination") {
    it("separates paired and unpaired filters") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languagesFilter = LanguagesFilter(Seq("eng"))
      val builder = new WorkFiltersAndAggregationsBuilder(
        aggregationRequests =
          List(WorkAggregationRequest.Format, WorkAggregationRequest.License),
        filters = List(formatFilter, languagesFilter, VisibleWorkFilter),
        requestToAggregation = requestToAggregation,
        filterToQuery = filterToQuery
      )

      builder.pairedFilters should contain only formatFilter
      builder.unpairedFilters should contain only (languagesFilter, VisibleWorkFilter)
    }

    it("handles the case where all filters are unpaired") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languagesFilter = LanguagesFilter(Seq("eng"))
      val builder = new WorkFiltersAndAggregationsBuilder(
        aggregationRequests = List(WorkAggregationRequest.License),
        filters = List(formatFilter, languagesFilter, VisibleWorkFilter),
        requestToAggregation = requestToAggregation,
        filterToQuery = filterToQuery
      )

      builder.pairedFilters should have length 0
      builder.unpairedFilters should contain only (languagesFilter, formatFilter, VisibleWorkFilter)
    }

    it("handles the case where all filters are paired") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languagesFilter = LanguagesFilter(Seq("en"))
      val builder = new WorkFiltersAndAggregationsBuilder(
        aggregationRequests =
          List(WorkAggregationRequest.Format, WorkAggregationRequest.Languages),
        filters = List(formatFilter, languagesFilter),
        requestToAggregation = requestToAggregation,
        filterToQuery = filterToQuery
      )

      builder.pairedFilters should contain only (formatFilter, languagesFilter)
      builder.unpairedFilters should have length 0
    }
  }

  describe("aggregation-level filtering") {
    it("applies to aggregations with a paired filter") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languagesFilter = LanguagesFilter(Seq("en"))
      val builder = new WorkFiltersAndAggregationsBuilder(
        aggregationRequests =
          List(WorkAggregationRequest.Format, WorkAggregationRequest.Languages),
        filters = List(formatFilter, languagesFilter),
        requestToAggregation = requestToAggregation,
        filterToQuery = filterToQuery
      )

      builder.filteredAggregations should have length 2
      builder.filteredAggregations.head shouldBe a[MockAggregation]
      val agg = builder.filteredAggregations.head.asInstanceOf[MockAggregation]
      agg.subaggs.head shouldBe a[FilterAggregation]
      agg.request shouldBe WorkAggregationRequest.Format
    }

    it("does not apply to aggregations without a paired filter") {
      val languagesFilter = LanguagesFilter(Seq("en"))
      val builder = new WorkFiltersAndAggregationsBuilder(
        aggregationRequests = List(WorkAggregationRequest.Format),
        filters = List(languagesFilter),
        requestToAggregation = requestToAggregation,
        filterToQuery = filterToQuery
      )

      builder.filteredAggregations should have length 1
      builder.filteredAggregations.head shouldBe a[MockAggregation]
      builder.filteredAggregations.head
        .asInstanceOf[MockAggregation]
        .subaggs should have length 0
    }

    it("applies paired filters to non-paired aggregations") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val builder = new WorkFiltersAndAggregationsBuilder(
        aggregationRequests =
          List(WorkAggregationRequest.Format, WorkAggregationRequest.Languages),
        filters = List(formatFilter),
        requestToAggregation = requestToAggregation,
        filterToQuery = filterToQuery
      )

      builder.filteredAggregations should have length 2
      val formatAgg =
        builder.filteredAggregations.head.asInstanceOf[MockAggregation]
      val languageAgg =
        builder.filteredAggregations(1).asInstanceOf[MockAggregation]
      formatAgg.subaggs.size shouldBe 0
      languageAgg.subaggs.head
        .asInstanceOf[FilterAggregation]
        .query
        .asInstanceOf[BoolQuery]
        .filters should contain only MockQuery(formatFilter)
    }

    it("applies all other aggregation-dependent filters to the paired filter") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languagesFilter = LanguagesFilter(Seq("en"))
      val genreFilter = GenreFilter("durian")
      val builder = new WorkFiltersAndAggregationsBuilder(
        aggregationRequests = List(
          WorkAggregationRequest.Format,
          WorkAggregationRequest.Languages,
          WorkAggregationRequest.Genre),
        filters = List(formatFilter, languagesFilter, genreFilter),
        requestToAggregation = requestToAggregation,
        filterToQuery = filterToQuery
      )

      val agg =
        builder.filteredAggregations.head
          .asInstanceOf[MockAggregation]
          .subaggs
          .head
          .asInstanceOf[FilterAggregation]
      agg.query shouldBe a[BoolQuery]
      val query = agg.query.asInstanceOf[BoolQuery]
      query.filters should not contain MockQuery(formatFilter)
      query.filters should contain only (MockQuery(languagesFilter), MockQuery(
        genreFilter))
    }
  }

  private def requestToAggregation(
    request: WorkAggregationRequest): Aggregation =
    MockAggregation("cabbage", request)

  private def filterToQuery(filter: WorkFilter): Query = MockQuery(filter)

  private case class MockQuery(filter: WorkFilter) extends Query

  private case class MockAggregation(name: String,
                                     request: WorkAggregationRequest,
                                     subaggs: Seq[AbstractAggregation] = Nil,
                                     metadata: Map[String, AnyRef] = Map.empty)
      extends Aggregation {
    type T = MockAggregation
    override def subAggregations(aggs: Iterable[AbstractAggregation]): T =
      copy(subaggs = aggs.toSeq)
    override def metadata(map: Map[String, AnyRef]): T = copy(metadata = map)
  }
}
