package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticError
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.generators.{
  ContributorGenerators,
  GenreGenerators,
  SubjectGenerators,
  WorksGenerators
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
    extends FunSpec
    with Matchers
    with ElasticsearchFixtures
    with ScalaFutures
    with SearchOptionsGenerators
    with SubjectGenerators
    with GenreGenerators
    with WorksGenerators
    with ContributorGenerators {

  val searchService = new ElasticsearchService(
    elasticClient = elasticClient
  )

  val defaultQueryOptions: ElasticsearchQueryOptions =
    createElasticsearchQueryOptions
  describe("queryResults") {
    describe("Failures") {
      it("returns a Left[ElasticError] if Elasticsearch returns an error") {
        val future = searchService
          .queryResults(Index("doesnotexist"), defaultQueryOptions)

        whenReady(future) { response =>
          response.isLeft shouldBe true
          response.left.get shouldBe a[ElasticError]
        }
      }
    }

    describe("Sorting") {
      it("returns results in consistent sort order") {
        withLocalWorksIndex { index =>
          val title =
            s"A ${Random.alphanumeric.filterNot(_.equals('A')) take 10 mkString}"

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
              .queryResults(
                index,
                createElasticsearchQueryOptionsWith(
                  searchQuery = Some(SearchQuery("A"))))

            whenReady(searchResponseFuture) { response =>
              searchResponseToWorks(response) shouldBe works
            }
          }
        }
      }

      it("sorts results with no SearchQuery by canonicalId") {
        withLocalWorksIndex { index =>
          val work1 = createIdentifiedWorkWith(canonicalId = "000Z")
          val work2 = createIdentifiedWorkWith(canonicalId = "000Y")
          val work3 = createIdentifiedWorkWith(canonicalId = "000X")

          insertIntoElasticsearch(index, work1, work2, work3)

          assertListResultsAreCorrect(
            index = index,
            expectedWorks = List(work3, work2, work1)
          )
        }
      }
    }

    describe("Filters") {
      it("filters search results by workType") {
        withLocalWorksIndex { index =>
          val workWithCorrectWorkType = createIdentifiedWorkWith(
            title = Some("Animated artichokes"),
            workType = Some(WorkType(id = "b", label = "Books"))
          )
          val workWithWrongTitle = createIdentifiedWorkWith(
            title = Some("Bouncing bananas"),
            workType = Some(WorkType(id = "b", label = "Books"))
          )
          val workWithWrongWorkType = createIdentifiedWorkWith(
            title = Some("Animated artichokes"),
            workType = Some(WorkType(id = "m", label = "Manuscripts"))
          )

          insertIntoElasticsearch(
            index,
            workWithCorrectWorkType,
            workWithWrongTitle,
            workWithWrongWorkType)

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery("artichokes")),
              filters = List(WorkTypeFilter(Seq("b")))
            ),
            expectedWorks = List(workWithCorrectWorkType)
          )
        }
      }

      it("filters search results with multiple workTypes") {
        withLocalWorksIndex { index =>
          val work1 = createIdentifiedWorkWith(
            title = Some("Animated artichokes"),
            workType = Some(WorkType(id = "b", label = "Books"))
          )
          val workWithWrongTitle = createIdentifiedWorkWith(
            title = Some("Bouncing bananas"),
            workType = Some(WorkType(id = "b", label = "Books"))
          )
          val work2 = createIdentifiedWorkWith(
            title = Some("Animated artichokes"),
            workType = Some(WorkType(id = "m", label = "Manuscripts"))
          )
          val workWithWrongType = createIdentifiedWorkWith(
            title = Some("Animated artichokes"),
            workType = Some(WorkType(id = "a", label = "Archives"))
          )

          insertIntoElasticsearch(
            index,
            work1,
            workWithWrongTitle,
            work2,
            workWithWrongType)

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery("artichokes")),
              filters = List(WorkTypeFilter(List("b", "m")))
            ),
            expectedWorks = List(work1, work2)
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

          assertSearchResultsAreCorrect(
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

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery("tangerines")),
              filters = List(ItemLocationTypeFilter(
                locationTypeIds = List("iiif-image", "digit")))
            ),
            expectedWorks = List(work, work2)
          )
        }
      }
    }

    describe("ID search with SearchQuery") {
      it("searches the canonicalId") {
        withLocalWorksIndex { index =>
          val work = createIdentifiedWorkWith(
            canonicalId = "abc123"
          )

          insertIntoElasticsearch(index, work)

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery("abc123"))),
            expectedWorks = List(work)
          )
        }
      }

      it("searches the sourceIdentifiers") {
        withLocalWorksIndex { index =>
          val work = createIdentifiedWorkWith(
            canonicalId = "abc123",
            sourceIdentifier = createSourceIdentifierWith()
          )
          val workNotMatching = createIdentifiedWorkWith(
            canonicalId = "123abc",
            sourceIdentifier = createSourceIdentifierWith()
          )
          val query = work.sourceIdentifier.value

          insertIntoElasticsearch(index, work, workNotMatching)
          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery(query))),
            expectedWorks = List(work)
          )
        }
      }

      it("searches the otherIdentifiers") {
        withLocalWorksIndex { index =>
          val work = createIdentifiedWorkWith(
            canonicalId = "abc123",
            otherIdentifiers = List(createSourceIdentifierWith())
          )
          val workNotMatching = createIdentifiedWorkWith(
            canonicalId = "123abc",
            otherIdentifiers = List(createSourceIdentifierWith())
          )
          val query = work.otherIdentifiers.head.value

          insertIntoElasticsearch(index, work, workNotMatching)

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery(query))),
            expectedWorks = List(work)
          )
        }
      }

      it("searches the items.canonicalId as keyword") {
        withLocalWorksIndex { index =>
          val work = createIdentifiedWorkWith(
            canonicalId = "abc123",
            items = List(createIdentifiedItemWith(canonicalId = "def"))
          )
          val workNotMatching = createIdentifiedWorkWith(
            canonicalId = "123abc",
            items = List(createIdentifiedItemWith(canonicalId = "def456"))
          )
          val query = "def"

          insertIntoElasticsearch(index, work, workNotMatching)

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery(query))),
            expectedWorks = List(work)
          )
        }
      }

      it("searches the items.sourceIdentifiers") {
        withLocalWorksIndex { index =>
          val work = createIdentifiedWorkWith(
            canonicalId = "abc123",
            items = List(
              createIdentifiedItemWith(sourceIdentifier =
                createSourceIdentifierWith(value = "sourceIdentifier123")))
          )
          val workNotMatching = createIdentifiedWorkWith(
            canonicalId = "123abc",
            items = List(
              createIdentifiedItemWith(sourceIdentifier =
                createSourceIdentifierWith(value = "sourceIdentifier456")))
          )

          val query = "sourceIdentifier123"

          insertIntoElasticsearch(index, work, workNotMatching)

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery(query))),
            expectedWorks = List(work)
          )
        }
      }

      it("searches the items.otherIdentifiers") {
        withLocalWorksIndex { index =>
          val work = createIdentifiedWorkWith(
            canonicalId = "abc123",
            items = List(createIdentifiedItemWith(otherIdentifiers =
              List(createSourceIdentifierWith(value = "sourceIdentifier123"))))
          )
          val workNotMatching = createIdentifiedWorkWith(
            canonicalId = "def456",
            items = List(createIdentifiedItemWith(otherIdentifiers =
              List(createSourceIdentifierWith(value = "sourceIdentifier456"))))
          )
          val query = "sourceIdentifier123"

          insertIntoElasticsearch(index, work, workNotMatching)

          assertSearchResultsAreCorrect(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery(query))),
            expectedWorks = List(work)
          )
        }
      }
    }

    describe("SearchQueryTypes and relevancy") {
      it("includes all query tokens from MSMBoostQueryUsingAndOperator") {
        withLocalWorksIndex { index =>
          // Longer text used to ensure signal in TF/IDF
          val works = List(
            "Lyrical Lychee",
            "Loose Lychee",
            "Lyrical Lime",
            "Loose Lime"
          ).map { t =>
            createIdentifiedWorkWith(title = Some(t))
          }

          insertIntoElasticsearch(index, works: _*)

          val results =
            searchResults(
              index = index,
              queryOptions = createElasticsearchQueryOptionsWith(
                searchQuery = Some(SearchQuery("Lyrical Lychee"))))

          results should have length 1
        }
      }
    }
  }
  describe("findResultById") {
    it("finds a result by ID") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWork

        insertIntoElasticsearch(index, work)

        val searchResultFuture =
          searchService.findResultById(canonicalId = work.canonicalId)(index)

        whenReady(searchResultFuture) { result =>
          val returnedWork =
            jsonToIdentifiedBaseWork(result.right.get.sourceAsString)
          returnedWork shouldBe work
        }
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      val future = searchService
        .findResultById("1234")(Index("doesnotexist"))

      whenReady(future) { response =>
        response.isLeft shouldBe true
        response.left.get shouldBe a[ElasticError]
      }
    }
  }

  describe("listResults") {
    it("returns everything if we ask for a limit > result size") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val queryOptions = createElasticsearchQueryOptionsWith(
          limit = works.length + 1
        )

        assertListResultsAreCorrect(
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

        assertListResultsAreCorrect(
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

        assertListResultsAreCorrect(
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

        assertListResultsAreCorrect(
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

        assertListResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = List()
        )
      }
    }

    it("does not list works that have visible=false") {
      withLocalWorksIndex { index =>
        val visibleWorks = createIdentifiedWorks(count = 8)
        val invisibleWorks = createIdentifiedInvisibleWorks(count = 2)

        val works = visibleWorks ++ invisibleWorks
        insertIntoElasticsearch(index, works: _*)

        assertListResultsAreCorrect(
          index = index,
          expectedWorks = visibleWorks
        )
      }
    }

    it("filters list results by workType") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          workType = Some(WorkType(id = "b", label = "Books"))
        )
        val work2 = createIdentifiedWorkWith(
          workType = Some(WorkType(id = "b", label = "Books"))
        )
        val workWithWrongWorkType = createIdentifiedWorkWith(
          workType = Some(WorkType(id = "m", label = "Manuscripts"))
        )

        insertIntoElasticsearch(index, work1, work2, workWithWrongWorkType)

        val queryOptions = createElasticsearchQueryOptionsWith(
          filters = List(WorkTypeFilter(Seq("b")))
        )

        assertListResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = List(work1, work2)
        )
      }
    }

    it("filters list results with multiple workTypes") {
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
        val workWithWrongWorkType = createIdentifiedWorkWith(
          workType = Some(WorkType(id = "m", label = "Manuscripts"))
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

        assertListResultsAreCorrect(
          index = index,
          queryOptions = queryOptions,
          expectedWorks = List(work1, work2, work3)
        )
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      val future = searchService
        .listResults(Index("doesnotexist"), defaultQueryOptions)

      whenReady(future) { response =>
        response.isLeft shouldBe true
        response.left.get shouldBe a[ElasticError]
      }
    }
  }

  private def createItemWithLocationType(
    locationType: LocationType): Identified[Item] =
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
      searchService.queryResults(index, queryOptions)
    whenReady(searchResponseFuture) { response =>
      searchResponseToWorks(response)
    }
  }

  private def assertSearchResultsAreCorrect(
    index: Index,
    queryOptions: ElasticsearchQueryOptions,
    expectedWorks: List[IdentifiedWork]) = {
    searchResults(index, queryOptions) should contain theSameElementsAs expectedWorks
  }

  private def assertListResultsAreCorrect(
    index: Index,
    queryOptions: ElasticsearchQueryOptions = createElasticsearchQueryOptions,
    expectedWorks: Seq[IdentifiedWork]
  ): Assertion = {
    val listResponseFuture = searchService
      .listResults(index, queryOptions)

    whenReady(listResponseFuture) { response =>
      searchResponseToWorks(response) should contain theSameElementsAs expectedWorks
    }
  }

  private def searchResponseToWorks(
    response: Either[ElasticError, SearchResponse]): List[IdentifiedBaseWork] =
    response.right.get.hits.hits.map { searchHit: SearchHit =>
      jsonToIdentifiedBaseWork(searchHit.sourceAsString)
    }.toList

  private def jsonToIdentifiedBaseWork(document: String): IdentifiedBaseWork =
    fromJson[IdentifiedBaseWork](document).get
}
