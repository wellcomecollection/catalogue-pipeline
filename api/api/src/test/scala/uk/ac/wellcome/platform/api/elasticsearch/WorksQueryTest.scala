package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.{ElasticError, Index}
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.work.generators.{
  ContributorGenerators,
  GenreGenerators,
  ImageGenerators,
  SubjectGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal.{CollectionPath, IdentifiedBaseWork}
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import uk.ac.wellcome.platform.api.models.{SearchQuery, SearchQueryType}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.api.services.{
  ElasticsearchQueryOptions,
  ElasticsearchService,
  WorksRequestBuilder
}

import scala.concurrent.ExecutionContext.Implicits.global

class WorksQueryTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with ScalaFutures
    with SearchOptionsGenerators
    with SubjectGenerators
    with GenreGenerators
    with WorksGenerators
    with ImageGenerators
    with ContributorGenerators {

  val searchService = new ElasticsearchService(elasticClient)

  describe("Free text query functionality") {

    it("searches the canonicalId") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          canonicalId = "abc123"
        )

        val query = "abc123"

        insertIntoElasticsearch(index, work)

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
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

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
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

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
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

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
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

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
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

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
      }
    }

    it("searches the images.canonicalId as keyword") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          canonicalId = "abc123",
          images = List(
            createUnmergedImage.toIdentifiedWith(id = "def")
          ))
        val workNotMatching = createIdentifiedWorkWith(
          canonicalId = "123abc",
          images = List(
            createUnmergedImage.toIdentifiedWith(id = "def456")
          )
        )
        val query = "def"

        insertIntoElasticsearch(index, work, workNotMatching)

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
      }
    }

    it("searches the images.sourceIdentifiers") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          canonicalId = "abc123",
          images = List(
            createUnmergedImageWith(identifierValue = "sourceIdentifier123").toIdentified
          )
        )
        val workNotMatching = createIdentifiedWorkWith(
          canonicalId = "123abc",
          images = List(
            createUnmergedImageWith(identifierValue = "sourceIdentifier456").toIdentified
          )
        )

        val query = "sourceIdentifier123"

        insertIntoElasticsearch(index, work, workNotMatching)

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
      }
    }

    it("matches when searching for an ID") {
      withLocalWorksIndex { index =>
        val work = createIdentifiedWorkWith(
          canonicalId = "abc123"
        )
        val query = "abc123"

        insertIntoElasticsearch(index, work)

        assertResultsMatchForAllowedQueryTypes(index, query, List(work))
      }
    }

    it("doesn't match on partial IDs") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          canonicalId = "1234567"
        )
        val query = "123"

        insertIntoElasticsearch(index, work1)

        assertResultsMatchForAllowedQueryTypes(index, query, List())
      }
    }

    it("matches IDs case insensitively") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          canonicalId = "AbCDeF1234"
        )
        val nonMatchingWork = createIdentifiedWorkWith(
          canonicalId = "bloopybloop"
        )
        val query = "abcdef1234"

        insertIntoElasticsearch(index, work1, nonMatchingWork)

        assertResultsMatchForAllowedQueryTypes(index, query, List(work1))
      }
    }

    it("matches multiple IDs") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          canonicalId = "AbCDeF1234"
        )
        val work2 = createIdentifiedWorkWith(
          canonicalId = "rstYui786"
        )
        val nonMatchingWork = createIdentifiedWorkWith(
          canonicalId = "bloopybloop"
        )
        val query = "abcdef1234 rstyui786"

        insertIntoElasticsearch(index, work1, work2, nonMatchingWork)

        assertResultsMatchForAllowedQueryTypes(index, query, List(work1, work2))
      }
    }

    it("doesn't match partially matching IDs") {
      withLocalWorksIndex { index =>
        val work1 = createIdentifiedWorkWith(
          canonicalId = "AbCDeF1234"
        )
        val work2 = createIdentifiedWorkWith(
          canonicalId = "rstYui786"
        )
        val nonMatchingWork1 = createIdentifiedWorkWith(
          canonicalId = "bloopybloop"
        )
        // We've put spaces in this as some Miro IDs are sentences
        val nonMatchingWork2 = createIdentifiedWorkWith(
          canonicalId = "Oxford english dictionary"
        )
        val query = "abcdef1234 rstyui786 Oxford"

        insertIntoElasticsearch(
          index,
          work1,
          work2,
          nonMatchingWork1,
          nonMatchingWork2)

        assertResultsMatchForAllowedQueryTypes(index, query, List(work1, work2))
      }
    }

    it("Searches for contributors") {
      withLocalWorksIndex { index =>
        val matchingWork = createIdentifiedWorkWith(
          contributors = List(createPersonContributorWith("Matching"))
        )
        val notMatchingWork = createIdentifiedWorkWith(
          contributors = List(createPersonContributorWith("Notmatching"))
        )

        val query = "matching"

        insertIntoElasticsearch(index, matchingWork, notMatchingWork)

        assertResultsMatchForAllowedQueryTypes(index, query, List(matchingWork))
      }
    }

    it("Searches for genres") {
      withLocalWorksIndex { index =>
        val matchingWork = createIdentifiedWorkWith(
          genres = List(createGenreWithMatchingConcept("Matching"))
        )
        val notMatchingWork = createIdentifiedWorkWith(
          genres = List(createGenreWithMatchingConcept("Notmatching"))
        )

        val query = "matching"

        insertIntoElasticsearch(index, matchingWork, notMatchingWork)

        assertResultsMatchForAllowedQueryTypes(index, query, List(matchingWork))
      }
    }

    it("Searches for subjects") {
      withLocalWorksIndex { index =>
        val matchingWork = createIdentifiedWorkWith(
          subjects = List(createSubjectWithMatchingConcept("Matching"))
        )
        val notMatchingWork = createIdentifiedWorkWith(
          subjects = List(createSubjectWithMatchingConcept("Notmatching"))
        )

        val query = "matching"

        insertIntoElasticsearch(index, matchingWork, notMatchingWork)

        assertResultsMatchForAllowedQueryTypes(index, query, List(matchingWork))
      }
    }

    it("Searches for collection in collectionPath.path") {
      withLocalWorksIndex { index =>
        val matchingWork = createIdentifiedWorkWith(
          collectionPath = Some(CollectionPath("PPCPB", label = Some("PP/CRI")))
        )
        val notMatchingWork = createIdentifiedWorkWith(
          collectionPath =
            Some(CollectionPath("NUFFINK", label = Some("NUF/FINK")))
        )
        val query = "PPCPB"
        insertIntoElasticsearch(index, matchingWork, notMatchingWork)
        assertResultsMatchForAllowedQueryTypes(index, query, List(matchingWork))
      }
    }
  }

  it("Searches for collection in collectionPath.label") {
    withLocalWorksIndex { index =>
      val matchingWork = createIdentifiedWorkWith(
        collectionPath = Some(CollectionPath("PPCPB", label = Some("PP/CRI")))
      )
      val notMatchingWork = createIdentifiedWorkWith(
        collectionPath =
          Some(CollectionPath("NUFFINK", label = Some("NUF/FINK")))
      )
      val query = "PP/CRI"
      insertIntoElasticsearch(index, matchingWork, notMatchingWork)
      assertResultsMatchForAllowedQueryTypes(index, query, List(matchingWork))
    }
  }

  private def assertResultsMatchForAllowedQueryTypes(
    index: Index,
    query: String,
    matches: List[IdentifiedBaseWork]) = {

    SearchQueryType.allowed map { queryType =>
      val results = searchResults(
        index,
        queryOptions = createElasticsearchQueryOptionsWith(
          searchQuery = Some(SearchQuery(query, queryType))))

      withClue(s"Using: ${queryType.name}") {
        results.size shouldBe matches.size
        results should contain theSameElementsAs (matches)
      }
    }
  }

  private def searchResults(index: Index,
                            queryOptions: ElasticsearchQueryOptions) = {
    val searchResponseFuture =
      searchService.executeSearch(queryOptions, WorksRequestBuilder, index)
    whenReady(searchResponseFuture) { response =>
      searchResponseToWorks(response)
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
