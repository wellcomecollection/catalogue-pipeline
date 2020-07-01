package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.search
import com.sksamuel.elastic4s.Index
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.{
  ContributorGenerators,
  GenreGenerators,
  SubjectGenerators,
  WorksGenerators
}
import uk.ac.wellcome.platform.api.generators.SearchOptionsGenerators
import uk.ac.wellcome.platform.api.services.ElasticsearchService
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
import org.scalatest.funspec.AnyFunSpec

class QueryVariantsTest
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

  /**
    * These test are more to explain what the query is doing
    * not to test every eventuality what they might do.
    */
  describe("BoolBoosted") {
    it("Has genre / subject / contributor / title tiered scoring") {
      withLocalWorksIndex { index =>
        val workWithMatchingTitleGenresSubjectsContributors =
          createIdentifiedWorkWith(
            canonicalId = "5",
            title = Some("Rain in spain"),
            genres = List(createGenreWithMatchingConcept("Rain in spain")),
            subjects = List(createSubjectWithMatchingConcept("Rain in spain")),
            contributors = List(createPersonContributorWith("Rain in spain")),
          )

        val workWithMatchingTitleGenresSubjects =
          createIdentifiedWorkWith(
            canonicalId = "4",
            title = Some("Rain in spain"),
            genres = List(createGenreWithMatchingConcept("Rain in spain")),
            subjects = List(createSubjectWithMatchingConcept("Rain in spain"))
          )

        val workWithMatchingTitleGenres =
          createIdentifiedWorkWith(
            canonicalId = "3",
            title = Some("Rain in spain"),
            genres = List(createGenreWithMatchingConcept("Rain in spain"))
          )

        val workWithMatchingGenre =
          createIdentifiedWorkWith(
            title = None,
            canonicalId = "genre1",
            genres = List(createGenreWithMatchingConcept("Rain in spain"))
          )

        val workWithMatchingBetterGenre =
          createIdentifiedWorkWith(
            title = None,
            canonicalId = "genre2betterMatching",
            genres = List(createGenreWithMatchingConcept("Rain in rain"))
          )

        val workWithMatchingTitle =
          createIdentifiedWorkWith(
            canonicalId = "title1",
            title = Some("Rain in spain")
          )

        val workWithMatchingBetterTitle =
          createIdentifiedWorkWith(
            canonicalId = "title2betterMatching",
            title = Some("Rain in rain")
          )

        insertIntoElasticsearch(
          index,
          Random.shuffle(
            List(
              workWithMatchingTitleGenresSubjectsContributors,
              workWithMatchingTitleGenresSubjects,
              workWithMatchingTitleGenres,
              workWithMatchingBetterGenre,
              workWithMatchingGenre,
              workWithMatchingBetterTitle,
              workWithMatchingTitle,
            )): _*
        )

        /**
          * BoolBoosted respects the tiered structure.
          * BoolBoosted multiplies the tf/idf score, so the nuance within each tier is respected.
          * BoolBoosted is the boss of you.
          */
        whenReady(elasticQuery(index, BoolBoostedQuery("Rain"))) { resp =>
          resp.hits.hits.map(_.id).toList should be(
            List(
              "5",
              "4",
              "3",
              "genre2betterMatching",
              "genre1",
              "title2betterMatching",
              "title1"))
        }
      }
    }
  }

  describe("PhraserBeam") {
    it("Matches scores by exact match and then by phrase match") {
      withLocalWorksIndex { index =>
        insertIntoElasticsearch(
          index,
          List(
            ("4ExactMatch", "Krishna"),
            ("3PhraseMatch", "Krishna shoe young"),
            ("2PhraseMatch", "Krishna (Hindu diety)"),
            ("1NoMatch", "Krish"))
            .map(
              s =>
                createIdentifiedWorkWith(
                  canonicalId = s._1,
                  title = Some(s._2))): _*
        )

        whenReady(elasticQuery(index, PhraserBeamQuery("Krishna"))) { resp =>
          resp.hits.hits.map(_.id).toList should be(
            List(
              "4ExactMatch",
              "2PhraseMatch",
              "3PhraseMatch",
            ))
        }
      }
    }
  }

  private def elasticQuery(index: Index, query: ElasticsearchQuery) = {
    val request = search(index)
      .query(
        query.elasticQuery
      )
      .sortBy(
        fieldSort("_score").order(SortOrder.DESC),
        fieldSort("canonicalId").order(SortOrder.ASC))
      .explain(true)

    elasticClient.execute {
      request
    } map { response =>
      {
        if (response.isError) {
          Left(response.error)
        } else {
          Right(response.result)
        }
      }.right.get
    }
  }
}
