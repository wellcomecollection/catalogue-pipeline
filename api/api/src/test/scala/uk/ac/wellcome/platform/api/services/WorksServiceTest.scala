package uk.ac.wellcome.platform.api.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.sksamuel.elastic4s.{ElasticError, Index}
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import uk.ac.wellcome.display.models.AggregationRequest
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.{
  ProductionEventGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.Format.{
  ArchivesAndManuscripts,
  Audio,
  Books,
  CDRoms,
  ManuscriptsAsian
}
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import uk.ac.wellcome.platform.api.models._
import WorkState.Identified

class WorksServiceTest
    extends AnyFunSpec
    with ElasticsearchFixtures
    with Matchers
    with ScalaFutures
    with SearchOptionsGenerators
    with WorksGenerators
    with ProductionEventGenerators {

  val elasticsearchService = new ElasticsearchService(elasticClient)

  val worksService = new WorksService(
    searchService = elasticsearchService
  )

  val defaultWorksSearchOptions = createWorksSearchOptions

  describe("listOrSearchWorks") {
    it("gets records in Elasticsearch") {
      val works = createIdentifiedWorks(count = 2)

      assertListOrSearchResultIsCorrect(
        allWorks = works,
        expectedWorks = works,
        expectedTotalResults = 2
      )
    }

    it("returns 0 pages when no results are available") {
      assertListOrSearchResultIsCorrect(
        allWorks = Seq(),
        expectedWorks = Seq(),
        expectedTotalResults = 0
      )
    }

    it("returns an empty result set when asked for a page that does not exist") {
      assertListOrSearchResultIsCorrect(
        allWorks = createIdentifiedWorks(count = 3),
        expectedWorks = Seq(),
        expectedTotalResults = 3,
        worksSearchOptions = createWorksSearchOptionsWith(pageNumber = 4)
      )
    }

    it("does not list invisible works") {
      val visibleWorks = createIdentifiedWorks(3)
      val invisibleWorks = createIdentifiedInvisibleWorks(3)

      assertListOrSearchResultIsCorrect(
        allWorks = visibleWorks ++ invisibleWorks,
        expectedWorks = visibleWorks,
        expectedTotalResults = visibleWorks.size,
        worksSearchOptions = createWorksSearchOptions
      )
    }

    it("filters records by format") {
      val work1 = createIdentifiedWorkWith(
        format = Some(ManuscriptsAsian)
      )
      val work2 = createIdentifiedWorkWith(
        format = Some(ManuscriptsAsian)
      )
      val workWithWrongFormat = createIdentifiedWorkWith(
        format = Some(CDRoms)
      )

      assertListOrSearchResultIsCorrect(
        allWorks = Seq(work1, work2, workWithWrongFormat),
        expectedWorks = Seq(work1, work2),
        expectedTotalResults = 2,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = List(FormatFilter(Seq("b")))
        )
      )
    }

    it("filters records by multiple formats") {
      val work1 = createIdentifiedWorkWith(
        format = Some(ManuscriptsAsian)
      )
      val work2 = createIdentifiedWorkWith(
        format = Some(ManuscriptsAsian)
      )
      val work3 = createIdentifiedWorkWith(
        format = Some(Books)
      )
      val workWithWrongFormat = createIdentifiedWorkWith(
        format = Some(CDRoms)
      )

      assertListOrSearchResultIsCorrect(
        allWorks = Seq(work1, work2, work3, workWithWrongFormat),
        expectedWorks = Seq(work1, work2, work3),
        expectedTotalResults = 3,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = List(FormatFilter(List("b", "a")))
        )
      )
    }

    it("returns a Left[ElasticError] if there's an Elasticsearch error") {
      val future = worksService.listOrSearchWorks(
        index = Index("doesnotexist"),
        searchOptions = defaultWorksSearchOptions
      )

      whenReady(future) { result =>
        result.isLeft shouldBe true
        result.left.get shouldBe a[ElasticError]
      }
    }

    it("only finds results that match a query if doing a full-text search") {
      val workDodo = createIdentifiedWorkWith(
        title = Some("A drawing of a dodo")
      )
      val workMouse = createIdentifiedWorkWith(
        title = Some("A mezzotint of a mouse")
      )

      assertListOrSearchResultIsCorrect(
        allWorks = List(workDodo, workMouse),
        expectedWorks = List(),
        expectedTotalResults = 0,
        worksSearchOptions =
          createWorksSearchOptionsWith(searchQuery = Some(SearchQuery("cat")))
      )

      assertListOrSearchResultIsCorrect(
        allWorks = List(workDodo, workMouse),
        expectedWorks = List(workDodo),
        expectedTotalResults = 1,
        worksSearchOptions =
          createWorksSearchOptionsWith(searchQuery = Some(SearchQuery("dodo")))
      )
    }

    it("doesn't throw an exception if passed an invalid query string") {
      val workEmu = createIdentifiedWorkWith(
        title = Some("An etching of an emu")
      )

      // unmatched quotes are a lexical error in the Elasticsearch parser
      assertListOrSearchResultIsCorrect(
        allWorks = List(workEmu),
        expectedWorks = List(workEmu),
        expectedTotalResults = 1,
        worksSearchOptions = createWorksSearchOptionsWith(
          searchQuery = Some(SearchQuery("emu \"")))
      )
    }
  }

  describe("simple query string syntax") {
    it("uses only PHRASE simple query syntax") {
      val work = createIdentifiedWorkWith(
        title = Some(
          "+a -title | with (all the simple) query~4 syntax operators in it*")
      )

      assertListOrSearchResultIsCorrect(
        allWorks = List(work),
        expectedWorks = List(work),
        expectedTotalResults = 1,
        worksSearchOptions = createWorksSearchOptionsWith(
          searchQuery = Some(SearchQuery(
            "+a -title | with (all the simple) query~4 syntax operators in it*")))
      )
    }

    it(
      "doesn't throw a too_many_clauses exception when passed a query that creates too many clauses") {
      val work = createIdentifiedWorkWith(
        title = Some("(a b c d e) h")
      )

      // This query uses precedence and would exceed the default 1024 clauses
      assertListOrSearchResultIsCorrect(
        allWorks = List(work),
        expectedWorks = List(work),
        expectedTotalResults = 1,
        worksSearchOptions = createWorksSearchOptionsWith(
          searchQuery = Some(SearchQuery("(a b c d e) h")))
      )
    }

    it("aggregates formats") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          format = Some(Books)
        )
        val work2 = createIdentifiedWorkWith(
          format = Some(Books)
        )
        val work3 = createIdentifiedWorkWith(
          format = Some(Audio)
        )
        val work4 = createIdentifiedWorkWith(
          format = Some(ArchivesAndManuscripts)
        )

        val worksSearchOptions =
          createWorksSearchOptionsWith(
            aggregations = List(AggregationRequest.Format))

        val expectedAggregations = Aggregations(
          Some(
            Aggregation(
              List(
                AggregationBucket(data = Books, count = 2),
                AggregationBucket(data = ArchivesAndManuscripts, count = 1),
                AggregationBucket(data = Audio, count = 1),
              ))),
          None
        )

        assertListOrSearchResultIsCorrect(
          allWorks = List(work1, work2, work3, work4),
          expectedWorks = List(work1, work2, work3, work4),
          expectedTotalResults = 4,
          expectedAggregations = Some(expectedAggregations),
          worksSearchOptions = worksSearchOptions
        )
      }
    }
  }

  describe("filter works by date") {

    val (work1, work2, work3) = (
      createDatedWork("1709"),
      createDatedWork("1950"),
      createDatedWork("2000")
    )
    val allWorks = Seq(work1, work2, work3)

    val (fromDate, toDate) = {
      val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
      (
        LocalDate.parse("01/01/1900", formatter),
        LocalDate.parse("01/01/1962", formatter)
      )
    }

    it("filters records by date range") {

      assertListOrSearchResultIsCorrect(
        allWorks = allWorks,
        expectedWorks = Seq(work2),
        expectedTotalResults = 1,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = DateRangeFilter(Some(fromDate), Some(toDate)) :: Nil
        )
      )
    }

    it("filters records by from date") {
      assertListOrSearchResultIsCorrect(
        allWorks = allWorks,
        expectedWorks = Seq(work2, work3),
        expectedTotalResults = 2,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = DateRangeFilter(Some(fromDate), None) :: Nil
        )
      )
    }

    it("filters records by to date") {
      assertListOrSearchResultIsCorrect(
        allWorks = allWorks,
        expectedWorks = Seq(work1, work2),
        expectedTotalResults = 2,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = DateRangeFilter(None, Some(toDate)) :: Nil
        )
      )
    }
  }

  describe("findWorkById") {
    it("gets a DisplayWork by id") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWork

        insertIntoElasticsearch(index, work)

        val future =
          worksService.findWorkById(canonicalId = work.state.canonicalId)(index)

        whenReady(future) { response =>
          response.isRight shouldBe true

          val records = response.right.get
          records.isDefined shouldBe true
          records.get shouldBe work
        }
      }

    }

    it("returns a future of None if it cannot get a record by id") {
      withLocalWorksIndex { index =>
        val recordsFuture =
          worksService.findWorkById(canonicalId = "1234")(index)

        whenReady(recordsFuture) { result =>
          result.isRight shouldBe true
          result.right.get shouldBe None
        }
      }
    }

    it("returns a Left[ElasticError] if there's an Elasticsearch error") {
      val future = worksService.findWorkById(
        canonicalId = "1234"
      )(
        index = Index("doesnotexist")
      )

      whenReady(future) { result =>
        result.isLeft shouldBe true
        result.left.get shouldBe a[ElasticError]
      }
    }
  }

  private def assertListOrSearchResultIsCorrect(
    allWorks: Seq[Work[Identified]],
    expectedWorks: Seq[Work[Identified]],
    expectedTotalResults: Int,
    expectedAggregations: Option[Aggregations] = None,
    worksSearchOptions: SearchOptions = createWorksSearchOptions
  ): Assertion =
    assertResultIsCorrect(
      worksService.listOrSearchWorks
    )(
      allWorks,
      expectedWorks,
      expectedTotalResults,
      expectedAggregations,
      worksSearchOptions)

  private def assertResultIsCorrect(
    partialSearchFunction: (Index, SearchOptions) => Future[
      Either[ElasticError, ResultList[Work.Visible[Identified], Aggregations]]]
  )(
    allWorks: Seq[Work[Identified]],
    expectedWorks: Seq[Work[Identified]],
    expectedTotalResults: Int,
    expectedAggregations: Option[Aggregations],
    worksSearchOptions: SearchOptions
  ): Assertion =
    withLocalWorksIndex { index =>
      if (allWorks.nonEmpty) {
        insertIntoElasticsearch(index, allWorks: _*)
      }

      val future = partialSearchFunction(index, worksSearchOptions)

      whenReady(future) { result =>
        result.isRight shouldBe true

        val works = result.right.get
        works.results should contain theSameElementsAs expectedWorks
        works.totalResults shouldBe expectedTotalResults
        works.aggregations shouldBe expectedAggregations
      }
    }
}
