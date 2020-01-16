import com.sksamuel.elastic4s.{ElasticError, Index}
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.work.generators.{
  ContributorGenerators,
  GenreGenerators,
  SubjectGenerators,
  WorksGenerators
}
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import uk.ac.wellcome.platform.api.models.{SearchQuery, SearchQueryType}
import uk.ac.wellcome.platform.api.services.{
  ElasticsearchQueryOptions,
  ElasticsearchService
}
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticsearchQueryTest
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

  describe("ScoringTiers") {
    it("returns all results in a tiered order") {
      withLocalWorksIndex { index =>
        // Longer text used to ensure signal in TF/IDF
        val titledWorks = List(
          "Gray's Inn.",
          "Loose Lychee",
          "Gray, John",
          "Gray's Inn Hall.",
          "Poems by Mr. Gray.",
          "A brief history of 'Gray's anatomy'",
          "H. Gray, Anatomy descriptive and surgical",
          "Gray's anatomy, descriptive and applied.",
          "Gray's anatomy.",
        ).map { t =>
          createIdentifiedWorkWith(canonicalId = t, title = Some(t))
        }

        val subjectedWorks = List(
          ("exact match subject", "Gray's Anatomy"),
          ("partial match subject", "Anatomy"),
        ).map {
          case (id, subject) =>
            createIdentifiedWorkWith(
              canonicalId = id,
              title = Some(s"subjected $subject"),
              subjects = List(createSubjectWithConcept(subject)))
        }
        val insertedWorks = titledWorks ++ subjectedWorks
        insertIntoElasticsearch(index, insertedWorks: _*)

        val results =
          searchResults(
            index = index,
            queryOptions = createElasticsearchQueryOptionsWith(
              searchQuery = Some(SearchQuery("Gray's anatomy"))))

        withClue(
          "a MUST query is used on the base query so as not to match everything") {
          (results.size < insertedWorks.size) should be(true)
        }

        withClue("the exact title should be first") {
          results.head should be(getWorkWithId("Gray's anatomy.", results))
        }

        withClue(
          "should find only subjects matching on AND operator and order it highly") {
          results(1) should be(getWorkWithId("exact match subject", results))
        }
      }
    }

    describe("FixedFields") {
      it("returns all results in a tiered order") {
        withLocalWorksIndex { index =>
          // Longer text used to ensure signal in TF/IDF
          val titledWorks = List(
            "Gray's Inn.",
            "Loose Lychee",
            "Gray, John",
            "Gray's Inn Hall.",
            "Poems by Mr. Gray.",
            "A brief history of 'Gray's anatomy'",
            "H. Gray, Anatomy descriptive and surgical",
            "Gray's anatomy, descriptive and applied.",
            "Gray's anatomy.",
          ).map { t =>
            createIdentifiedWorkWith(canonicalId = t, title = Some(t))
          }

          val subjectedWorks = List(
            ("exact match subject", "Gray's Anatomy"),
            ("partial match subject", "Anatomy"),
          ).map {
            case (id, subject) =>
              createIdentifiedWorkWith(
                canonicalId = id,
                title = Some(s"subjected $subject"),
                subjects = List(createSubjectWithConcept(subject)))
          }
          val insertedWorks = titledWorks ++ subjectedWorks
          insertIntoElasticsearch(index, insertedWorks: _*)

          val results =
            searchResults(
              index = index,
              queryOptions = createElasticsearchQueryOptionsWith(
                searchQuery = Some(SearchQuery("Gray's anatomy"))))

          withClue(
            "a MUST query is used on the base query so as not to match everything") {
            (results.size < insertedWorks.size) should be(true)
          }

          withClue("the exact title should be first") {
            results.head should be(getWorkWithId("Gray's anatomy.", results))
          }

          withClue(
            "should find only subjects matching on AND operator and order it highly") {
            results(1) should be(getWorkWithId("exact match subject", results))
          }
        }
      }

      it("should use the english analyser in the base query") {
        withLocalWorksIndex { index =>
          val works = List(
            "Vlad the impaler",
            "Dad the impala",
          ).map { t =>
            createIdentifiedWorkWith(title = Some(t))
          }

          insertIntoElasticsearch(index, works: _*)

          // If we search the non-english analysed fields with the base query
          // `the` would in the search as we're using the `OR` operator
          // and would be matched in both examples above as the root field
          // (not `field` rather than `field.english`, see `WorksIndex.scala`)
          // does not use the english analyser.

          // We wouldn't want to use the english analyser at query time though
          // as we would lose detail used in other where we use exact matching
          val results =
            searchResults(
              index = index,
              queryOptions = createElasticsearchQueryOptionsWith(
                searchQuery = Some(
                  SearchQuery("vlad the impaler", SearchQueryType.FixedFields)))
            )

          results should contain theSameElementsAs List(works.head)
        }
      }

      it("AND scores heavily on the contributors field") {
        withLocalWorksIndex { index =>
          val workWithExactMatchingContributors =
            createIdentifiedWorkWith(
              contributors = List(
                createPersonContributorWith("Alice Stewart"),
                createPersonContributorWith("Honor Fell"),
              ))

          val workWithPartialMatchingContributors = createIdentifiedWorkWith(
            contributors = List(
              createPersonContributorWith("Alice Fell"),
            ))

          insertIntoElasticsearch(
            index,
            workWithPartialMatchingContributors,
            workWithExactMatchingContributors)
          val results =
            searchResults(
              index = index,
              queryOptions = createElasticsearchQueryOptionsWith(searchQuery =
                Some(SearchQuery("Alice Stewart", SearchQueryType.FixedFields)))
            )

          results.head should be(workWithExactMatchingContributors)
          results.last should be(workWithPartialMatchingContributors)
        }
      }
    }
  }

  private def getWorkWithId(
    id: String,
    works: List[IdentifiedBaseWork]): IdentifiedBaseWork =
    works.find(work => work.canonicalId == id).get

  private def searchResults(index: Index,
                            queryOptions: ElasticsearchQueryOptions) = {
    val searchResponseFuture =
      searchService.queryResults(index, queryOptions)
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
