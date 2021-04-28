package uk.ac.wellcome.models.index

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.work.generators.WorkGenerators
import weco.catalogue.internal_model.generators.ImageGenerators
import uk.ac.wellcome.elasticsearch.IndexConfig
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import io.circe._, io.circe.generic.semiauto._, io.circe.syntax._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.searches._

case class TestDoc(label: String)
object TestDoc {
  implicit val fooDecoder: Decoder[TestDoc] = deriveDecoder
  implicit val fooEncoder: Encoder[TestDoc] = deriveEncoder
}

object TestIndexConfig extends IndexConfig with IndexConfigFields {
  val analysis = WorksAnalysis()
  val mapping = properties(
    label
  ).dynamic(DynamicMapping.Strict)
}

class IndexConfigFieldsTest
    extends AnyFunSpec
    with IndexFixtures
    with ScalaFutures
    with Eventually
    with Matchers
    with JsonAssertions
    with ScalaCheckPropertyChecks
    with WorkGenerators
    with ImageGenerators {

  def indexDocs(index: Index, docs: TestDoc*) {
    elasticClient.execute {
      bulk(
        docs.map(
          doc =>
            indexInto(index.name)
              .doc(doc.asJson.noSpaces.toString)
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

  describe("label field") {
    val doc1 = TestDoc("Arkaprakāśa Yōkai Amabié")
    val doc2 = TestDoc("PM/RT/TYR")

    it("text matches") {
      withLocalIndex(TestIndexConfig) { index =>
        indexDocs(index, doc1, doc2)
        expectResultsSize(search(index).matchQuery("label", "Yōkai"), 1)
      }
    }

    it("asciifolds lowercases") {
      withLocalIndex(TestIndexConfig) { index =>
        indexDocs(index, doc1, doc2)
        expectResultsSize(
          search(index).matchQuery("label", "arkaprakasa yokai"),
          1
        )
      }
    }

    it("case sensitive keyword on `label.keyword`") {
      withLocalIndex(TestIndexConfig) { index =>
        indexDocs(index, doc1, doc2)
        expectResultsSize(search(index).prefix("label.keyword", "PM/RT"), 1)
        expectResultsSize(search(index).prefix("label.keyword", "pm/rt"), 0)
      }
    }

    it("case insensitive keyword matches on `label.lowercaseKeyword`") {
      withLocalIndex(TestIndexConfig) { index =>
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
}
