package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.aggs.{
  AbstractAggregation,
  Aggregation,
  FilterAggregation
}
import com.sksamuel.elastic4s.requests.searches.queries.{BoolQuery, Query}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.display.models.AggregationRequest
import uk.ac.wellcome.platform.api.models._

class FiltersAndAggregationsBuilderTest extends AnyFunSpec with Matchers {

  describe("filter discrimination") {
    it("separates paired and unpaired filters") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FiltersAndAggregationsBuilder(
        List(AggregationRequest.Format, AggregationRequest.License),
        List(formatFilter, languageFilter, VisibleWorkFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.pairedFilters should contain only formatFilter
      sut.unpairedFilters should contain only (languageFilter, VisibleWorkFilter)
    }

    it("handles the case where all filters are unpaired") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FiltersAndAggregationsBuilder(
        List(AggregationRequest.License),
        List(formatFilter, languageFilter, VisibleWorkFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.pairedFilters should have length 0
      sut.unpairedFilters should contain only (languageFilter, formatFilter, VisibleWorkFilter)
    }

    it("handles the case where all filters are paired") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FiltersAndAggregationsBuilder(
        List(AggregationRequest.Format, AggregationRequest.Language),
        List(formatFilter, languageFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.pairedFilters should contain only (formatFilter, languageFilter)
      sut.unpairedFilters should have length 0
    }
  }

  describe("aggregation-level filtering") {
    it("applies to aggregations with a paired filter") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FiltersAndAggregationsBuilder(
        List(AggregationRequest.Format, AggregationRequest.Language),
        List(formatFilter, languageFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.filteredAggregations should have length 2
      sut.filteredAggregations.head shouldBe a[MockAggregation]
      val agg = sut.filteredAggregations.head.asInstanceOf[MockAggregation]
      agg.subaggs.head shouldBe a[FilterAggregation]
      agg.request shouldBe AggregationRequest.Format
    }

    it("does not apply to aggregations without a paired filter") {
      val languageFilter = LanguageFilter(Seq("en"))
      val sut = new FiltersAndAggregationsBuilder(
        List(AggregationRequest.Format),
        List(languageFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.filteredAggregations should have length 1
      sut.filteredAggregations.head shouldBe a[MockAggregation]
      sut.filteredAggregations.head
        .asInstanceOf[MockAggregation]
        .subaggs should have length 0
    }

    it("applies paired filters to non-paired aggregations") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val sut = new FiltersAndAggregationsBuilder(
        List(AggregationRequest.Format, AggregationRequest.Language),
        List(formatFilter),
        requestToAggregation,
        filterToQuery
      )

      sut.filteredAggregations should have length 2
      val formatAgg =
        sut.filteredAggregations.head.asInstanceOf[MockAggregation]
      val languageAgg =
        sut.filteredAggregations(1).asInstanceOf[MockAggregation]
      formatAgg.subaggs.size shouldBe 0
      languageAgg.subaggs.head
        .asInstanceOf[FilterAggregation]
        .query
        .asInstanceOf[BoolQuery]
        .filters should contain only MockQuery(formatFilter)
    }

    it("applies all other aggregation-dependent filters to the paired filter") {
      val formatFilter = FormatFilter(Seq("bananas"))
      val languageFilter = LanguageFilter(Seq("en"))
      val genreFilter = GenreFilter("durian")
      val sut = new FiltersAndAggregationsBuilder(
        List(
          AggregationRequest.Format,
          AggregationRequest.Language,
          AggregationRequest.Genre),
        List(formatFilter, languageFilter, genreFilter),
        requestToAggregation,
        filterToQuery
      )

      val agg =
        sut.filteredAggregations.head
          .asInstanceOf[MockAggregation]
          .subaggs
          .head
          .asInstanceOf[FilterAggregation]
      agg.query shouldBe a[BoolQuery]
      val query = agg.query.asInstanceOf[BoolQuery]
      query.filters should not contain MockQuery(formatFilter)
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
