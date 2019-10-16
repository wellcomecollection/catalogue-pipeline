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
import uk.ac.wellcome.platform.api.models.WorkQuery._
import uk.ac.wellcome.platform.api.models.{
  ItemLocationTypeFilter,
  WorkQuery,
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

  describe("simpleStringQueryResults") {
    it("filters search results by workType") {
      withLocalWorksIndex { index =>
        val workWithCorrectWorkType = createIdentifiedWorkWith(
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

        insertIntoElasticsearch(
          index,
          workWithCorrectWorkType,
          workWithWrongTitle,
          workWithWrongWorkType)

        assertSearchResultsAreCorrect(
          index = index,
          workQuery = MSMBoostQuery("artichokes"),
          queryOptions = createElasticsearchQueryOptionsWith(
            filters = List(WorkTypeFilter("b"))
          ),
          expectedWorks = List(workWithCorrectWorkType)
        )
      }
    }

    it("filters search results with multiple workTypes") {
      withLocalWorksIndex { index =>
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
        val workWithWrongType = createIdentifiedWorkWith(
          title = "Animated artichokes",
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
          workQuery = MSMBoostQuery("artichokes"),
          queryOptions = createElasticsearchQueryOptionsWith(
            filters = List(WorkTypeFilter(List("b", "m")))
          ),
          expectedWorks = List(work1, work2)
        )
      }
    }

    it("filters results by item locationType") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          title = "Tumbling tangerines",
          items = List(
            createItemWithLocationType(LocationType("iiif-image")),
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        val notMatchingWork = createIdentifiedWorkWith(
          title = "Tumbling tangerines",
          items = List(
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        insertIntoElasticsearch(index, work, notMatchingWork)

        assertSearchResultsAreCorrect(
          index = index,
          workQuery = MSMBoostQuery("tangerines"),
          queryOptions = createElasticsearchQueryOptionsWith(
            filters = List(ItemLocationTypeFilter("iiif-image"))
          ),
          expectedWorks = List(work)
        )
      }
    }

    it("filters results by multiple item locationTypes") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          title = "Tumbling tangerines",
          items = List(
            createItemWithLocationType(LocationType("iiif-image")),
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        val notMatchingWork = createIdentifiedWorkWith(
          title = "Tumbling tangerines",
          items = List(
            createItemWithLocationType(LocationType("acqi"))
          )
        )

        val work2 = createIdentifiedWorkWith(
          title = "Tumbling tangerines",
          items = List(
            createItemWithLocationType(LocationType("digit"))
          )
        )

        insertIntoElasticsearch(index, work, notMatchingWork, work2)

        assertSearchResultsAreCorrect(
          index = index,
          workQuery = MSMBoostQuery("tangerines"),
          queryOptions = createElasticsearchQueryOptionsWith(
            filters = List(
              ItemLocationTypeFilter(
                locationTypeIds = List("iiif-image", "digit")))
          ),
          expectedWorks = List(work, work2)
        )
      }
    }

    it("returns results in consistent order") {
      withLocalWorksIndex { index =>
        val title =
          s"A ${Random.alphanumeric.filterNot(_.equals('A')) take 10 mkString}"

        // We have a secondary sort on canonicalId in ElasticsearchService.
        // Since every work has the same title, we expect them to be returned in
        // ID order when we search for "A".
        val works = (1 to 5)
          .map { _ =>
            createIdentifiedWorkWith(title = title)
          }
          .sortBy(_.canonicalId)

        insertIntoElasticsearch(index, works: _*)

        (1 to 10).foreach { _ =>
          val searchResponseFuture = searchService
            .queryResults(MSMBoostQuery("A"))(index, defaultQueryOptions)

          whenReady(searchResponseFuture) { response =>
            searchResponseToWorks(response) shouldBe works
          }
        }
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      val future = searchService
        .queryResults(MSMBoostQuery("cat"))(
          Index("doesnotexist"),
          defaultQueryOptions)

      whenReady(future) { response =>
        response.isLeft shouldBe true
        response.left.get shouldBe a[ElasticError]
      }
    }
  }

  describe("searches IDs") {
    it("searches the canonicalId") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          canonicalId = "abc123"
        )

        insertIntoElasticsearch(index, work)

        assertSearchResultsAreCorrect(
          index = index,
          workQuery = MSMBoostQuery("abc123"),
          queryOptions = defaultQueryOptions,
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
          workQuery = MSMBoostQuery(query),
          queryOptions = defaultQueryOptions,
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
          workQuery = MSMBoostQuery(query),
          queryOptions = defaultQueryOptions,
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
          workQuery = MSMBoostQuery(query),
          queryOptions = defaultQueryOptions,
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
          workQuery = MSMBoostQuery(query),
          queryOptions = defaultQueryOptions,
          expectedWorks = List(work)
        )
      }
    }

    it("searches the items.otherIdentifiers") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          canonicalId = "abc123",
          items = List(
            createIdentifiedItemWith(otherIdentifiers =
              List(createSourceIdentifierWith(value = "sourceIdentifier123"))))
        )
        val workNotMatching = createIdentifiedWorkWith(
          canonicalId = "def456",
          items = List(
            createIdentifiedItemWith(otherIdentifiers =
              List(createSourceIdentifierWith(value = "sourceIdentifier456"))))
        )
        val query = "sourceIdentifier123"

        insertIntoElasticsearch(index, work, workNotMatching)

        assertSearchResultsAreCorrect(
          index = index,
          workQuery = MSMBoostQuery(query),
          queryOptions = defaultQueryOptions,
          expectedWorks = List(work)
        )
      }
    }
  }

  describe("searches using Selectable queries") {
    val noMatch =
      createIdentifiedWorkWith(title = "Before a Bengal")

    it("finds results for a MSMBoostQuery search") {
      withLocalWorksIndex { index =>
        // Longer text used to ensure signal in TF/IDF
        val matchingTitle100 =
          createIdentifiedWorkWith(
            canonicalId = "matchingTitle100",
            title = "Title text that contains Aegean")
        val matchingTitle75 =
          createIdentifiedWorkWith(
            canonicalId = "matchingTitle75",
            title = "Title text that contains")
        val matchingTitle25 =
          createIdentifiedWorkWith(
            canonicalId = "matchingTitle25",
            title = "Title text")

        val matchingSubject100 =
          createIdentifiedWorkWith(
            canonicalId = "matchingSubject100",
            subjects =
              List(createSubjectWith(s"Subject text that contains Aegean")))
        val matchingSubject75 =
          createIdentifiedWorkWith(
            canonicalId = "matchingSubject75",
            subjects = List(createSubjectWith(s"Subject text that contains")))
        val matchingSubject25 =
          createIdentifiedWorkWith(
            canonicalId = "matchingSubject25",
            subjects = List(createSubjectWith(s"Subject text")))

        val matchingGenre100 =
          createIdentifiedWorkWith(
            canonicalId = "matchingGenre100",
            genres = List(createGenreWith(s"Subject text that contains Aegean")))
        val matchingGenre75 =
          createIdentifiedWorkWith(
            canonicalId = "matchingGenre75",
            genres = List(createGenreWith(s"Subject text that contains")))
        val matchingGenre25 =
          createIdentifiedWorkWith(
            canonicalId = "matchingGenre25",
            genres = List(createGenreWith(s"Subject text")))

        val matchingDescription100 =
          createIdentifiedWorkWith(
            canonicalId = "matchingDescription100",
            description = Some(s"Description text that contains Aegean"))
        val matchingDescription75 =
          createIdentifiedWorkWith(
            canonicalId = "matchingDescription75",
            description = Some(s"Description text that contains"))
        val matchingDescription25 =
          createIdentifiedWorkWith(
            canonicalId = "matchingDescription25",
            description = Some(s"Description text"))

        insertIntoElasticsearch(
          index,
          noMatch,
          matchingTitle100,
          matchingTitle75,
          matchingTitle25,
          matchingSubject100,
          matchingSubject75,
          matchingSubject25,
          matchingGenre100,
          matchingGenre75,
          matchingGenre25,
          matchingDescription100,
          matchingDescription75,
          matchingDescription25
        )

        val results =
          searchResults(
            index = index,
            workQuery = MSMBoostQuery("Text that contains Aegean"))

        results should have length 10

        withClue("(0, 1) should be title matches") {
          results.slice(0, 2) shouldBe List(matchingTitle100, matchingTitle75)
        }

        withClue("(2, 3) should be 100 genre / subject matches") {
          results.slice(2, 4) should contain theSameElementsAs List(
            matchingGenre100,
            matchingSubject100)
        }

        withClue("(4) should be 25 title match matches") {
          results.slice(4, 5) should contain theSameElementsAs List(
            matchingTitle25)
        }

        withClue("(5, 6) should be 75 genre / subject matches") {
          results.slice(5, 7) should contain theSameElementsAs List(
            matchingGenre75,
            matchingSubject75)
        }

        withClue("(7, 8) should be 100 / 75 description matches") {
          results.slice(7, 9) shouldBe List(
            matchingDescription100,
            matchingDescription75)
        }

      }
    }

    it("excludes notes from MSMBoostQuery") {
      withLocalWorksIndex { index =>
        // Longer text used to ensure signal in TF/IDF
        val withNotes =
          createIdentifiedWorkWith(
            title = "Mermaids and Marmite",
            notes = List(GeneralNote("Aegean"), GeneralNote("Holiday snaps")))

        insertIntoElasticsearch(index, withNotes)

        val results =
          searchResults(
            index = index,
            workQuery = MSMBoostQuery("Aegean holiday snaps"))

        results should have length 0
      }
    }

    it("includes notes from MSMBoostQueryWithNotes") {
      withLocalWorksIndex { index =>
        // Longer text used to ensure signal in TF/IDF
        val withNotes =
          createIdentifiedWorkWith(
            title = "Mermaids and Marmite",
            notes = List(GeneralNote("Aegean"), GeneralNote("Holiday snaps")))

        insertIntoElasticsearch(index, withNotes)

        val results =
          searchResults(
            index = index,
            workQuery = MSMBoostQueryWithNotes("Aegean holiday snaps"))

        results should have length 1
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
    it("sorts results from Elasticsearch in the correct order") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(canonicalId = "000Z")
        val work2 = createIdentifiedWorkWith(canonicalId = "000Y")
        val work3 = createIdentifiedWorkWith(canonicalId = "000X")

        insertIntoElasticsearch(index, work1, work2, work3)

        assertListResultsAreCorrect(
          index = index,
          expectedWorks = List(work3, work2, work1)
        )

      // TODO: canonicalID is the only user-defined field that we can sort on.
      // When we have other fields we can sort on, we should extend this test
      // for different sort orders.
      }
    }

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
          filters = List(WorkTypeFilter("b"))
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
                            workQuery: WorkQuery,
                            queryOptions: ElasticsearchQueryOptions =
                              createElasticsearchQueryOptions) = {
    val searchResponseFuture =
      searchService.queryResults(workQuery)(index, queryOptions)
    whenReady(searchResponseFuture) { response =>
      searchResponseToWorks(response)
    }
  }

  private def assertSearchResultsAreCorrect(
    index: Index,
    workQuery: WorkQuery,
    queryOptions: ElasticsearchQueryOptions,
    expectedWorks: List[IdentifiedWork]) = {
    searchResults(index, workQuery, queryOptions) should contain theSameElementsAs expectedWorks
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
