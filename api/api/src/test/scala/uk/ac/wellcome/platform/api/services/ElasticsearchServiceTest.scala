package uk.ac.wellcome.platform.api.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import com.sksamuel.elastic4s.{ElasticError, Index}
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators._
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.index.IndexFixtures
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.locations._
import weco.catalogue.internal_model.work.Format._
import weco.catalogue.internal_model.work.{Item, Work}
import weco.catalogue.internal_model.work.WorkState.Indexed

class ElasticsearchServiceTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with SearchOptionsGenerators
    with ItemsGenerators
    with WorkGenerators {

  val searchService = new ElasticsearchService(elasticClient)

  val defaultSearchOptions =
    createWorksSearchOptions

  describe("findResultById") {
    it("finds a result by ID") {
      withLocalWorksIndex { index =>
        val work = indexedWork()

        insertIntoElasticsearch(index, work)

        val searchResultFuture =
          searchService.executeGet(canonicalId = work.state.canonicalId)(index)

        whenReady(searchResultFuture) { result =>
          val returnedWork =
            jsonToWork(result.right.get.sourceAsString)
          returnedWork shouldBe work
        }
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      val future = searchService
        .executeGet(createCanonicalId)(Index("doesnotexist"))

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

        val searchOptions = createWorksSearchOptionsWith(
          pageSize = works.length + 1
        )

        assertResultsAreCorrect(
          index = index,
          searchOptions = searchOptions,
          expectedWorks = works
        )
      }
    }

    it("returns a page from the beginning of the result set") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val searchOptions = createWorksSearchOptionsWith(pageSize = 4)

        assertResultsAreCorrect(
          index = index,
          searchOptions = searchOptions,
          expectedWorks = works.slice(0, 4)
        )
      }
    }

    it("returns a page from halfway through the result set") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val searchOptions = createWorksSearchOptionsWith(
          pageSize = 4,
          pageNumber = 2
        )

        assertResultsAreCorrect(
          index = index,
          searchOptions = searchOptions,
          expectedWorks = works.slice(4, 8)
        )
      }
    }

    it("returns a page from the end of the result set") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val searchOptions = createWorksSearchOptionsWith(
          pageSize = 7,
          pageNumber = 2
        )

        assertResultsAreCorrect(
          index = index,
          searchOptions = searchOptions,
          expectedWorks = works.slice(7, 10)
        )
      }
    }

    it("returns an empty page if asked for a limit > result size") {
      withLocalWorksIndex { index =>
        val works = populateElasticsearch(index)

        val searchOptions = createWorksSearchOptionsWith(
          pageSize = 1,
          pageNumber = works.length * 2
        )

        assertResultsAreCorrect(
          index = index,
          searchOptions = searchOptions,
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
            indexedWork().title(title)
          }
          .sortBy(_.state.canonicalId)

        insertIntoElasticsearch(index, works: _*)

        (1 to 10).foreach { _ =>
          val searchResponseFuture = searchService
            .executeSearch(
              createWorksSearchOptionsWith(
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
        val work1 = indexedWork(canonicalId = CanonicalId("0000000Z"))
        val work2 = indexedWork(canonicalId = CanonicalId("0000000Y"))
        val work3 = indexedWork(canonicalId = CanonicalId("0000000X"))

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
        val work1 = indexedWork(canonicalId = CanonicalId("0000000Z")).title("match")
        val work2 = indexedWork(canonicalId = CanonicalId("0000000Y")).title("match stick")
        val work3 = indexedWork(canonicalId = CanonicalId("0000000X")).title("match")

        insertIntoElasticsearch(index, work1, work2, work3)

        assertResultsAreCorrect(
          searchOptions = createWorksSearchOptionsWith(
            searchQuery = Some(SearchQuery("match"))),
          index = index,
          expectedWorks = List(work3, work1, work2),
          scored = Some(true)
        )
      }
    }

    it("filters list results by format") {
      withLocalWorksIndex { index =>
        val work1 = indexedWork().format(ManuscriptsAsian)
        val work2 = indexedWork().format(ManuscriptsAsian)
        val workWithWrongFormat = indexedWork().format(CDRoms)

        insertIntoElasticsearch(index, work1, work2, workWithWrongFormat)

        val searchOptions = createWorksSearchOptionsWith(
          filters = List(FormatFilter(Seq("b")))
        )

        assertResultsAreCorrect(
          index = index,
          searchOptions = searchOptions,
          expectedWorks = List(work1, work2)
        )
      }
    }

    it("filters list results with multiple formats") {
      withLocalWorksIndex { index =>
        val work1 = indexedWork().format(ManuscriptsAsian)
        val work2 = indexedWork().format(ManuscriptsAsian)
        val work3 = indexedWork().format(Books)
        val workWithWrongFormat = indexedWork().format(CDRoms)

        insertIntoElasticsearch(index, work1, work2, work3, workWithWrongFormat)

        val searchOptions = createWorksSearchOptionsWith(
          filters = List(FormatFilter(List("b", "a")))
        )

        assertResultsAreCorrect(
          index = index,
          searchOptions = searchOptions,
          expectedWorks = List(work1, work2, work3)
        )
      }
    }

    it("filters results by item locationType") {
      withLocalWorksIndex { index =>
        val work = indexedWork()
          .title("Tumbling tangerines")
          .items(
            List(
              createItemWithLocationType(LocationType.IIIFImageAPI),
              createItemWithLocationType(LocationType.ClosedStores)
            )
          )

        val notMatchingWork = indexedWork()
          .title("Tumbling tangerines")
          .items(
            List(
              createItemWithLocationType(LocationType.ClosedStores)
            )
          )

        insertIntoElasticsearch(index, work, notMatchingWork)

        assertResultsAreCorrect(
          index = index,
          searchOptions = createWorksSearchOptionsWith(
            searchQuery = Some(SearchQuery("tangerines")),
            filters = List(ItemLocationTypeIdFilter(Seq("iiif-image")))
          ),
          expectedWorks = List(work)
        )
      }
    }

    it("filters results by multiple item locationTypes") {
      withLocalWorksIndex { index =>
        val work =
          indexedWork()
            .title("Tumbling tangerines")
            .items(
              List(
                createItemWithLocationType(LocationType.IIIFImageAPI),
                createItemWithLocationType(LocationType.ClosedStores)
              )
            )

        val notMatchingWork =
          indexedWork()
            .title("Tumbling tangerines")
            .items(
              List(
                createItemWithLocationType(LocationType.ClosedStores)
              )
            )

        val work2 =
          indexedWork()
            .title("Tumbling tangerines")
            .items(
              List(
                createItemWithLocationType(LocationType.OpenShelves)
              )
            )

        insertIntoElasticsearch(index, work, notMatchingWork, work2)

        assertResultsAreCorrect(
          index = index,
          searchOptions = createWorksSearchOptionsWith(
            searchQuery = Some(SearchQuery("tangerines")),
            filters = List(
              ItemLocationTypeIdFilter(
                locationTypeIds = List("iiif-image", "open-shelves")))
          ),
          expectedWorks = List(work, work2)
        )
      }
    }

    it("returns a Left[ElasticError] if Elasticsearch returns an error") {
      val future = searchService
        .executeSearch(
          defaultSearchOptions,
          WorksRequestBuilder,
          Index("doesnotexist")
        )
      whenReady(future) { response =>
        response.isLeft shouldBe true
        response.left.get shouldBe a[ElasticError]
      }
    }
  }

  def createItemWithLocationType(
    locationType: LocationType): Item[IdState.Minted] =
    createIdentifiedItemWith(
      locations = List(
        locationType match {
          case LocationType.ClosedStores =>
            createPhysicalLocationWith(
              locationType = LocationType.ClosedStores,
              label = LocationType.ClosedStores.label
            )

          case physicalLocationType: PhysicalLocationType =>
            createPhysicalLocationWith(locationType = physicalLocationType)

          case digitalLocationType: DigitalLocationType =>
            createDigitalLocationWith(locationType = digitalLocationType)
        }
      )
    )

  private def populateElasticsearch(
    index: Index): List[Work.Visible[Indexed]] = {
    val works = indexedWorks(count = 10)

    insertIntoElasticsearch(index, works: _*)

    works.sortBy(_.state.canonicalId)
  }

  private def searchResults(index: Index, searchOptions: WorkSearchOptions) = {
    val searchResponseFuture =
      searchService.executeSearch(searchOptions, WorksRequestBuilder, index)
    whenReady(searchResponseFuture) { response =>
      searchResponseToWorks(response)
    }
  }

  private def assertResultsAreCorrect(
    index: Index,
    searchOptions: WorkSearchOptions = createWorksSearchOptions,
    expectedWorks: List[Work.Visible[Indexed]],
    scored: Option[Boolean] = None) = {
    searchResults(index, searchOptions) should contain theSameElementsAs expectedWorks
  }

  private def searchResponseToWorks(
    response: Either[ElasticError, SearchResponse]): List[Work[Indexed]] =
    response.right.get.hits.hits.map { searchHit: SearchHit =>
      jsonToWork(searchHit.sourceAsString)
    }.toList

  private def jsonToWork(document: String): Work[Indexed] =
    fromJson[Work[Indexed]](document).get
}
