package weco.catalogue.internal_model.index

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import weco.json.utils.JsonAssertions
import weco.elasticsearch.IndexConfig
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser.decode
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.common.Operator
import com.sksamuel.elastic4s.requests.searches._
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchQuery
import org.scalatest.prop.TableDrivenPropertyChecks

case class TestDoc(
  label: Option[String] = None,
  withSlashes: Option[String] = None
)
object TestDoc {
  implicit val testDocDecoder: Decoder[TestDoc] = deriveDecoder
  implicit val testDocEncoder: Encoder[TestDoc] = deriveEncoder
}

class IndexConfigFieldsTest
    extends AnyFunSpec
    with IndexFixtures
    with Matchers
    with JsonAssertions
    with ScalaCheckPropertyChecks
    with TableDrivenPropertyChecks
    with IndexConfigFields {

  val testIndexConfig = IndexConfig(
    {
      val withSlashesField = textField("withSlashes").analyzer(
        WorksAnalysis.slashesAnalyzer.name
      )

      properties(
        label,
        withSlashesField
      ).dynamic(DynamicMapping.Strict)
    },
    WorksAnalysis()
  )

  def indexDocs(index: Index, docs: TestDoc*) {
    elasticClient.execute {
      bulk(
        docs.map(
          doc =>
            indexInto(index.name)
              .doc(doc.asJson.noSpaces)
        )
      ).refreshImmediately
    }.await

    getSizeOf(index) shouldBe docs.size
  }

  def expectResultsSize(req: SearchRequest, expectedSize: Int) = {
    val res = elasticClient.execute(req).await
    res.isInstanceOf[RequestSuccess[SearchResponse]] shouldBe true
    res.result.hits.hits.toList.size shouldBe expectedSize
  }

  def expectResults(req: SearchRequest, results: List[TestDoc]) = {
    val res = elasticClient.execute(req).await
    res.isInstanceOf[RequestSuccess[SearchResponse]] shouldBe true
    res.result.hits.hits.toList
      .map(hit => decode[TestDoc](hit.sourceAsString).right.get) shouldBe results
  }

  describe("label field") {
    val doc1 = TestDoc(Some("Arkaprakāśa Yōkai Amabié"))
    val doc2 = TestDoc(Some("PM/RT/TYR"))

    it("text matches") {
      withLocalElasticsearchIndex(config = testIndexConfig) { index =>
        indexDocs(index, doc1, doc2)
        expectResultsSize(search(index).matchQuery("label", "Yōkai"), 1)
      }
    }

    it("asciifolds lowercases") {
      withLocalElasticsearchIndex(config = testIndexConfig) { index =>
        indexDocs(index, doc1, doc2)
        expectResultsSize(
          search(index).matchQuery("label", "arkaprakasa yokai"),
          1
        )
      }
    }

    it("case sensitive keyword on `label.keyword`") {
      withLocalElasticsearchIndex(config = testIndexConfig) { index =>
        indexDocs(index, doc1, doc2)
        expectResultsSize(search(index).prefix("label.keyword", "PM/RT"), 1)
        expectResultsSize(search(index).prefix("label.keyword", "pm/rt"), 0)
      }
    }

    it("case insensitive keyword matches on `label.lowercaseKeyword`") {
      withLocalElasticsearchIndex(config = testIndexConfig) { index =>
        indexDocs(index, doc1, doc2)
        expectResultsSize(
          search(index).prefix("label.lowercaseKeyword", "PM/RT"),
          1
        )
        expectResultsSize(
          search(index).prefix("label.lowercaseKeyword", "pm/rt"),
          1
        )
      }
    }
  }

  describe("withSlashes field") {
    val doc1 = TestDoc(withSlashes = Some("WA/HMM/BU Premises and Buildings"))
    val doc2 = TestDoc(
      withSlashes =
        Some("WA/HMM Wellcome Historical Medical Museum and Library")
    )

    def andQuery(query: String) =
      MatchQuery(
        "withSlashes",
        query,
        operator = Some(Operator.AND)
      )

    it("matches exactly and case insensitively with slashes") {
      withLocalElasticsearchIndex(config = testIndexConfig) { index =>
        indexDocs(index, doc1, doc2)

        val tests = Table(
          ("query", "results"),
          (andQuery("wa/hmm"), List(doc2)),
          (andQuery("wa/hmm wellcome museum"), List(doc2)),
          (andQuery("wA/hmM/bU PREMISES"), List(doc1)),
          // This would match on a standard text field as punctuation is removed
          // but shouldn't for our use case
          (andQuery("wA hmM bU PREMISES"), List()),
          // This is a case that should never really occur as people won't search for this
          // but is there to illustrate what's happening under that hood
          // i.e. `/` is mapped to `__`
          (andQuery("wA__hmM__bU PREMISES"), List(doc1))
        )

        forAll(tests) {
          case (query, results) =>
            expectResults(
              search(index).query(query),
              results
            )
        }
      }
    }
  }
}
