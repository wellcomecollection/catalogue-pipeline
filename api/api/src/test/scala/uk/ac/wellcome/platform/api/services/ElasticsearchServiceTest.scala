package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.{ElasticError, Index}
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.{
  ContributorGenerators,
  GenreGenerators,
  SubjectGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal.WorkType.{
  Books,
  CDRoms,
  ManuscriptsAsian
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import uk.ac.wellcome.platform.api.models.{
  ItemLocationTypeFilter,
  SearchQuery,
  WorkTypeFilter
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class ElasticsearchServiceTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with ScalaFutures
    with SearchOptionsGenerators
    with SubjectGenerators
    with GenreGenerators
    with WorksGenerators
    with ContributorGenerators {

  val searchService = new ElasticsearchService(elasticClient)

  val defaultQueryOptions: ElasticsearchQueryOptions =
    createElasticsearchQueryOptions

  describe("findResultById") {
    it("finds a result by ID") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWork

        insertIntoElasticsearch(index, work)

        val searchResultFuture =
          searchService.executeGet(canonicalId = work.canonicalId)(index)

        whenReady(searchResultFuture) { result =>
          val returnedWork =
            jsonToIdentifiedBaseWork(result.right.get.sourceAsString)
          returnedWork shouldBe work
        }
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      val future = searchService
        .executeGet("1234")(Index("doesnotexist"))

      whenReady(future) { response =>
        response.isLeft shouldBe true
        response.left.get shouldBe a[ElasticError]
      }
    }
  }

  describe("executeSearch") {
    it("returns everything if we ask for a limit > result size") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val queryOptions = createElasticsearchQueryOptionsWith(
          limit = works.length + 1
        )

        assertResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = works
        )
      }
    }

    it("returns a page from the beginning of the result set") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val queryOptions = createElasticsearchQueryOptionsWith(limit = 4)

        assertResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = works.slice(0, 4)
        )
      }
    }

    it("returns a page from halfway through the result set") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val queryOptions = createElasticsearchQueryOptionsWith(
          limit = 4,
          from = 3
        )

        assertResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = works.slice(3, 7)
        )
      }
    }

    it("returns a page from the end of the result set") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val queryOptions = createElasticsearchQueryOptionsWith(
          limit = 7,
          from = 5
        )

        assertResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = works.slice(5, 10)
        )
      }
    }

    it("returns an empty page if asked for a limit > result size") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val queryOptions = createElasticsearchQueryOptionsWith(
          from = works.length * 2
        )

        assertResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = List()
        )
      }
    }

    it("returns results in consistent sort order") {
      withLocalWorksIndex { index =>
        val title =
          s"ABBA ${Random.alphanumeric.filterNot(_.equals('A')) take 10 mkString}"

        // We have a secondary sort on canonicalId in ElasticsearchService.
        // Since every work has the same title, we expect them to be returned in
        // ID order when we search for "A".
        val works = (1 to 5)
          .map { _ =>
            createIdentifiedWorkWith(title = Some(title))
          }
          .sortBy(_.canonicalId)

        insertIntoElasticsearch(index, works: _*)

        (1 to 10).foreach { _ =>
          val searchResponseFuture = searchService
            .executeSearch(
              createElasticsearchQueryOptionsWith(
                searchQuery = Some(SearchQuery("abba"))),
              WorksRequestBuilder,
              index)

          whenReady(searchResponseFuture) { response =>
            searchResponseToWorks(response) shouldBe works
          }
        }
      }
    }

    it("sorts by canonicalId when scored = false") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(canonicalId = "000Z")
        val work2 = createIdentifiedWorkWith(canonicalId = "000Y")
        val work3 = createIdentifiedWorkWith(canonicalId = "000X")

        insertIntoElasticsearch(index, work1, work2, work3)

        assertResultsAreCorrect(
          index = index,
          expectedWorks = List(work3, work2, work1),
          scored = Some(false)
        )
      }
    }

    it("sorts by score then canonicalId when scored = true") {
      withLocalWorksIndex { index =>
        val work1 =
          createIdentifiedWorkWith(canonicalId = "000Z", title = Some("match"))
        val work2 = createIdentifiedWorkWith(
          canonicalId = "000Y",
          title = Some("match stick"))
        val work3 =
          createIdentifiedWorkWith(canonicalId = "000X", title = Some("match"))

        insertIntoElasticsearch(index, work1, work2, work3)

        assertResultsAreCorrect(
          queryOptions = createElasticsearchQueryOptionsWith(
            searchQuery = Some(SearchQuery("match"))),
          index = index,
          expectedWorks = List(work3, work1, work2),
          scored = Some(true)
        )
      }
    }

    it("filters list results by workType") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          workType = Some(ManuscriptsAsian)
        )
        val work2 = createIdentifiedWorkWith(
          workType = Some(ManuscriptsAsian)
        )
        val workWithWrongWorkType = createIdentifiedWorkWith(
          workType = Some(CDRoms)
        )

        insertIntoElasticsearch(index, work1, work2, workWithWrongWorkType)

        val queryOptions = createElasticsearchQueryOptionsWith(
          filters = List(WorkTypeFilter(Seq("b")))
        )

        assertResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = List(work1, work2)
        )
      }
    }

    it("filters list results with multiple workTypes") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          workType = Some(ManuscriptsAsian)
        )
        val work2 = createIdentifiedWorkWith(
          workType = Some(ManuscriptsAsian)
        )
        val work3 = createIdentifiedWorkWith(
          workType = Some(Books)
        )
        val workWithWrongWorkType = createIdentifiedWorkWith(
          workType = Some(CDRoms)
        )

        insertIntoElasticsearch(
          index,
          work1,
          work2,
          work3,
          workWithWrongWorkType)

        val queryOptions = createElasticsearchQueryOptionsWith(
          filters = List(WorkTypeFilter(List("b", "a")))
        )

        assertResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = List(work1, work2, work3)
        )
      }
    }

    it("filters results by item locationType") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          title = Some("Tumbling tangerines"),
          items = List(
            createItemWithLocationType(LocationType("iiif-image")),
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        val notMatchingWork = createIdentifiedWorkWith(
          title = Some("Tumbling tangerines"),
          items = List(
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        insertIntoElasticsearch(index, work, notMatchingWork)

        assertResultsAreCorrect(
          index = index,
          queryOptions = createElasticsearchQueryOptionsWith(
            searchQuery = Some(SearchQuery("tangerines")),
            filters = List(ItemLocationTypeFilter(Seq("iiif-image")))
          ),
          expectedWorks = List(work)
        )
      }
    }

    it("filters results by multiple item locationTypes") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          title = Some("Tumbling tangerines"),
          items = List(
            createItemWithLocationType(LocationType("iiif-image")),
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        val notMatchingWork = createIdentifiedWorkWith(
          title = Some("Tumbling tangerines"),
          items = List(
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        val work2 = createIdentifiedWorkWith(
          title = Some("Tumbling tangerines"),
          items = List(
            createItemWithLocationType(LocationType("digit"))
          )
        )

        insertIntoElasticsearch(index, work, notMatchingWork, work2)

        assertResultsAreCorrect(
          index = index,
          queryOptions = createElasticsearchQueryOptionsWith(
            searchQuery = Some(SearchQuery("tangerines")),
            filters = List(
              ItemLocationTypeFilter(
                locationTypeIds = List("iiif-image", "digit")))
          ),
          expectedWorks = List(work, work2)
        )
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      val future = searchService
        .executeSearch(
          defaultQueryOptions,
          WorksRequestBuilder,
          Index("doesnotexist")
        )
      whenReady(future) { response =>
        response.isLeft shouldBe true
        response.left.get shouldBe a[ElasticError]
      }
    }
  }

  private def createItemWithLocationType(
    locationType: LocationType): Item[Id.Minted] =
    createIdentifiedItemWith(
      locations = List(
        // This test really shouldn't be affected by physical/digital locations;
        // we just pick randomly here to ensure we get a good mixture.
        Random
          .shuffle(
            List(
              createPhysicalLocationWith(locationType = locationType),
              createDigitalLocationWith(locationType = locationType)
            ))
          .head
      )
    )

  private def populateElasticsearch(index: Index): List[IdentifiedWork] = {
    val works = createIdentifiedWorks(count = 10)

    insertIntoElasticsearch(index, works: _*)

    works.sortBy(_.canonicalId).toList
  }

  private def searchResults(index: Index,
                            queryOptions: ElasticsearchQueryOptions) = {
    val searchResponseFuture =
      searchService.executeSearch(queryOptions, WorksRequestBuilder, index)
    whenReady(searchResponseFuture) { response =>
      searchResponseToWorks(response)
    }
  }

  private def assertResultsAreCorrect(index: Index,
                                      queryOptions: ElasticsearchQueryOptions =
                                        createElasticsearchQueryOptions,
                                      expectedWorks: List[IdentifiedWork],
                                      scored: Option[Boolean] = None) = {
    searchResults(index, queryOptions) should contain theSameElementsAs expectedWorks
  }

  private def searchResponseToWorks(
    response: Either[ElasticError, SearchResponse]): List[IdentifiedBaseWork] =
    response.right.get.hits.hits.map { searchHit: SearchHit =>
      jsonToIdentifiedBaseWork(searchHit.sourceAsString)
    }.toList

  private def jsonToIdentifiedBaseWork(document: String): IdentifiedBaseWork =
    fromJson[IdentifiedBaseWork](document).get
}
