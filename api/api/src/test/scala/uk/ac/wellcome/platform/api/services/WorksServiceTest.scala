package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticError
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.{
  ProductionEventGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal.{IdentifiedBaseWork, WorkType}
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import uk.ac.wellcome.platform.api.models.{
  AggregationBuckets,
  AggregationResults,
  DateRangeFilter,
  ResultList,
  WorkTypeAggregationBucket,
  WorkTypeFilter
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import uk.ac.wellcome.display.models.WorkTypeAggregationRequest
import uk.ac.wellcome.platform.api.models.WorkQuery.MSMBoostQuery

class WorksServiceTest
    extends FunSpec
    with ElasticsearchFixtures
    with Matchers
    with ScalaFutures
    with SearchOptionsGenerators
    with WorksGenerators
    with ProductionEventGenerators {

  val elasticsearchService = new ElasticsearchService(
    elasticClient = elasticClient
  )

  val worksService = new WorksService(
    searchService = elasticsearchService
  )

  val defaultWorksSearchOptions = createWorksSearchOptions

  describe("listWorks") {
    it("gets records in Elasticsearch") {
      val works = createIdentifiedWorks(count = 2)

      assertListResultIsCorrect(
        allWorks = works,
        expectedWorks = works,
        expectedTotalResults = 2
      )
    }

    it("returns 0 pages when no results are available") {
      assertListResultIsCorrect(
        allWorks = Seq(),
        expectedWorks = Seq(),
        expectedTotalResults = 0
      )
    }

    it("returns an empty result set when asked for a page that does not exist") {
      assertListResultIsCorrect(
        allWorks = createIdentifiedWorks(count = 3),
        expectedWorks = Seq(),
        expectedTotalResults = 3,
        worksSearchOptions = createWorksSearchOptionsWith(pageNumber = 4)
      )
    }

    it("filters records by workType") {
      val work1 = createIdentifiedWorkWith(
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val work2 = createIdentifiedWorkWith(
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val workWithWrongWorkType = createIdentifiedWorkWith(
        workType = Some(WorkType(id = "m", label = "Manuscripts"))
      )

      assertListResultIsCorrect(
        allWorks = Seq(work1, work2, workWithWrongWorkType),
        expectedWorks = Seq(work1, work2),
        expectedTotalResults = 2,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = List(WorkTypeFilter(workTypeId = "b"))
        )
      )
    }

    it("filters records by multiple workTypes") {
      val work1 = createIdentifiedWorkWith(
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val work2 = createIdentifiedWorkWith(
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val work3 = createIdentifiedWorkWith(
        workType = Some(WorkType(id = "a", label = "Archives"))
      )
      val workWithWrongWorkType = createIdentifiedWorkWith(
        workType = Some(WorkType(id = "m", label = "Manuscripts"))
      )

      assertListResultIsCorrect(
        allWorks = Seq(work1, work2, work3, workWithWrongWorkType),
        expectedWorks = Seq(work1, work2, work3),
        expectedTotalResults = 3,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = List(WorkTypeFilter(List("b", "a")))
        )
      )
    }

    it("returns a Left[ElasticError] if there's an Elasticsearch error") {
      val future = worksService.listWorks(
        index = Index("doesnotexist"),
        worksSearchOptions = defaultWorksSearchOptions
      )

      whenReady(future) { result =>
        result.isLeft shouldBe true
        result.left.get shouldBe a[ElasticError]
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

      assertListResultIsCorrect(
        allWorks = allWorks,
        expectedWorks = Seq(work2),
        expectedTotalResults = 1,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = DateRangeFilter(Some(fromDate), Some(toDate)) :: Nil
        )
      )
    }

    it("filters records by from date") {
      assertListResultIsCorrect(
        allWorks = allWorks,
        expectedWorks = Seq(work2, work3),
        expectedTotalResults = 2,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = DateRangeFilter(Some(fromDate), None) :: Nil
        )
      )
    }

    it("filters records by to date") {
      assertListResultIsCorrect(
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
          worksService.findWorkById(canonicalId = work.canonicalId)(index)

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

  describe("searchWorks") {
    it("only finds results that match a query if doing a full-text search") {
      val workDodo = createIdentifiedWorkWith(
        title = "A drawing of a dodo"
      )
      val workMouse = createIdentifiedWorkWith(
        title = "A mezzotint of a mouse"
      )

      assertSearchResultIsCorrect(
        query = "cat"
      )(
        allWorks = List(workDodo, workMouse),
        expectedWorks = List(),
        expectedTotalResults = 0
      )

      assertSearchResultIsCorrect(
        query = "dodo"
      )(
        allWorks = List(workDodo, workMouse),
        expectedWorks = List(workDodo),
        expectedTotalResults = 1
      )
    }

    it("doesn't throw an exception if passed an invalid query string") {
      val workEmu = createIdentifiedWorkWith(
        title = "An etching of an emu"
      )

      // unmatched quotes are a lexical error in the Elasticsearch parser
      assertSearchResultIsCorrect(
        query = "emu \""
      )(
        allWorks = List(workEmu),
        expectedWorks = List(workEmu),
        expectedTotalResults = 1
      )
    }

    it("filters searches by workType") {
      val matchingWork = createIdentifiedWorkWith(
        title = "Animated artichokes",
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val workWithWrongTitle = createIdentifiedWorkWith(
        title = "Bouncing bananas",
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val workWithWrongWorkType = createIdentifiedWorkWith(
        title = "Animated artichokes",
        workType = Some(WorkType(id = "m", label = "Manuscripts"))
      )

      assertSearchResultIsCorrect(
        query = "artichokes"
      )(
        allWorks = List(matchingWork, workWithWrongTitle, workWithWrongWorkType),
        expectedWorks = List(matchingWork),
        expectedTotalResults = 1,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = List(WorkTypeFilter(workTypeId = "b"))
        )
      )
    }

    it("filters searches by multiple workTypes") {
      val work1 = createIdentifiedWorkWith(
        title = "Animated artichokes",
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val workWithWrongTitle = createIdentifiedWorkWith(
        title = "Bouncing bananas",
        workType = Some(WorkType(id = "b", label = "Books"))
      )
      val work2 = createIdentifiedWorkWith(
        title = "Animated artichokes",
        workType = Some(WorkType(id = "m", label = "Manuscripts"))
      )
      val workWithWrongWorkType = createIdentifiedWorkWith(
        title = "Animated artichokes",
        workType = Some(WorkType(id = "a", label = "Archives"))
      )

      assertSearchResultIsCorrect(
        query = "artichokes"
      )(
        allWorks = List(work1, workWithWrongTitle, work2, workWithWrongWorkType),
        expectedWorks = List(work1, work2),
        expectedTotalResults = 2,
        worksSearchOptions = createWorksSearchOptionsWith(
          filters = List(WorkTypeFilter(List("b", "m")))
        )
      )
    }

    it("returns a Left[ElasticError] if there's an Elasticsearch error") {
      val future = worksService.searchWorks(
        workQuery = MSMBoostQuery("cat")
      )(
        index = Index("doesnotexist"),
        worksSearchOptions = defaultWorksSearchOptions
      )

      whenReady(future) { result =>
        result.isLeft shouldBe true
        result.left.get shouldBe a[ElasticError]
      }
    }

    // See: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html#simple-query-string-syntax
    describe("simple query string syntax") {
      it("uses only PHRASE simple query syntax") {
        val work = createIdentifiedWorkWith(
          title =
            "+a -title | with (all the simple) query~4 syntax operators in it*"
        )

        assertSearchResultIsCorrect(query =
          "+a -title | with (all the simple) query~4 syntax operators in it*")(
          allWorks = List(work),
          expectedWorks = List(work),
          expectedTotalResults = 1
        )
      }

      it("doesn't throw a too_many_clauses exception when passed a query that creates too many clauses") {
        val workEmu = createIdentifiedWorkWith(
          title = "a b c"
        )

        // This query uses precedence and would exceed the default 1024 clauses
        assertSearchResultIsCorrect(
          query = "(a b c d e) h"
        )(
          allWorks = List(workEmu),
          expectedWorks = List(workEmu),
          expectedTotalResults = 1
        )
      }

      it("matches results with the PHRASE syntax (\"term\")") {
        val workExactTitle = createIdentifiedWorkWith(
          title = "An exact match of a title"
        )

        val workLooseTitle = createIdentifiedWorkWith(
          title = "A loose match of a title"
        )

        // Should return both
        assertSearchResultIsCorrect(
          query = "An exact match of a title"
        )(
          allWorks = List(workExactTitle, workLooseTitle),
          expectedWorks = List(workExactTitle, workLooseTitle),
          expectedTotalResults = 2,
          expectedAggregations = None
        )

        // Should return only the exact match
        assertSearchResultIsCorrect(
          query = "\"An exact match of a title\""
        )(
          allWorks = List(workExactTitle, workLooseTitle),
          expectedWorks = List(workExactTitle),
          expectedTotalResults = 1
        )
      }

      it("aggregates workTypes") {
        withLocalWorksIndex { index =>
          val work1 = createIdentifiedWorkWith(
            workType = Some(WorkType(id = "b", label = "Books"))
          )
          val work2 = createIdentifiedWorkWith(
            workType = Some(WorkType(id = "b", label = "Books"))
          )
          val work3 = createIdentifiedWorkWith(
            workType = Some(WorkType(id = "a", label = "Archives"))
          )
          val work4 = createIdentifiedWorkWith(
            workType = Some(WorkType(id = "m", label = "Manuscripts"))
          )

          val worksSearchOptions =
            createWorksSearchOptionsWith(
              aggregations = List(WorkTypeAggregationRequest()))

          val expectedAggregations = AggregationResults(
            Some(
              AggregationBuckets(List(
                WorkTypeAggregationBucket(key = WorkType("b", "Books"), 2),
                WorkTypeAggregationBucket(key = WorkType("a", "Archives"), 1),
                WorkTypeAggregationBucket(key = WorkType("b", "Manuscripts"), 1)
              )))
          )

          assertListResultIsCorrect(
            allWorks = List(work1, work2, work3, work4),
            expectedWorks = List(),
            expectedTotalResults = 4,
            expectedAggregations = Some(expectedAggregations),
            worksSearchOptions = worksSearchOptions
          )
        }
      }
    }
  }

  private def assertListResultIsCorrect(
    allWorks: Seq[IdentifiedBaseWork],
    expectedWorks: Seq[IdentifiedBaseWork],
    expectedTotalResults: Int,
    expectedAggregations: Option[AggregationResults] = None,
    worksSearchOptions: WorksSearchOptions = createWorksSearchOptions
  ): Assertion =
    assertResultIsCorrect(
      worksService.listWorks
    )(
      allWorks,
      expectedWorks,
      expectedTotalResults,
      expectedAggregations,
      worksSearchOptions)

  private def assertSearchResultIsCorrect(query: String)(
    allWorks: Seq[IdentifiedBaseWork],
    expectedWorks: Seq[IdentifiedBaseWork],
    expectedTotalResults: Int,
    expectedAggregations: Option[AggregationResults] = None,
    worksSearchOptions: WorksSearchOptions = createWorksSearchOptions
  ): Assertion =
    assertResultIsCorrect(
      worksService.searchWorks(MSMBoostQuery(query))
    )(
      allWorks,
      expectedWorks,
      expectedTotalResults,
      expectedAggregations,
      worksSearchOptions)

  private def assertResultIsCorrect(
    partialSearchFunction: (
      Index,
      WorksSearchOptions) => Future[Either[ElasticError, ResultList]]
  )(
    allWorks: Seq[IdentifiedBaseWork],
    expectedWorks: Seq[IdentifiedBaseWork],
    expectedTotalResults: Int,
    expectedAggregations: Option[AggregationResults],
    worksSearchOptions: WorksSearchOptions
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
