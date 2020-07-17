package uk.ac.wellcome.elasticsearch
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.requests.analysis.{
  Analysis,
  CustomAnalyzer,
  PathHierarchyTokenizer,
  ShingleTokenFilter,
  StemmerTokenFilter,
  TokenFilter
}

// TODO: Patch back to elastic4s
case class AsciiFoldingTokenFilter(override val name: String,
                                   preserveOriginal: Option[Boolean] = None)
    extends TokenFilter {

  override def build: XContentBuilder = {
    val b = XContentFactory.jsonBuilder()
    b.field("type", "asciifolding")
    preserveOriginal.foreach(b.field("preserve_original", _))
    b
  }
}

object WorksAnalysis {
  // Analysis
  val pathTokenizer = PathHierarchyTokenizer("path_hierarchy_tokenizer")

  val pathAnalyzer =
    CustomAnalyzer("path_hierarchy_analyzer", pathTokenizer.name, Nil, Nil)

  val asciiFoldingTokenFilter = AsciiFoldingTokenFilter(
    "asciifolding_token_filter",
    preserveOriginal = Some(true)
  )

  val shingleTokenFilter = ShingleTokenFilter(
    "shingle_token_filter",
    minShingleSize = Some(2),
    maxShingleSize = Some(4))

  val englishStemmerTokenFilter =
    StemmerTokenFilter("english_token_filter", lang = "english")

  val englishPossessiveStemmerTokenFilter =
    StemmerTokenFilter(
      "english_possessive_token_filter",
      lang = "possessive_english")

  val asciifoldingAnalyzer = CustomAnalyzer(
    "shingle_asciifolding_analyzer",
    tokenizer = "standard",
    tokenFilters =
      List("lowercase", shingleTokenFilter.name, asciiFoldingTokenFilter.name),
    charFilters = Nil
  )

  val englishAnalyzer = CustomAnalyzer(
    "english_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      asciiFoldingTokenFilter.name,
      englishStemmerTokenFilter.name,
      englishPossessiveStemmerTokenFilter.name),
    charFilters = Nil
  )

  val shingleAsciifoldingAnalyzer = CustomAnalyzer(
    "shingle_asciifolding_analyzer",
    tokenizer = "standard",
    tokenFilters =
      List("lowercase", shingleTokenFilter.name, asciiFoldingTokenFilter.name),
    charFilters = Nil
  )

  def apply(): Analysis = {
    Analysis(
      analyzers = List(
        pathAnalyzer,
        asciifoldingAnalyzer,
        shingleAsciifoldingAnalyzer,
        englishAnalyzer
      ),
      tokenFilters = List(
        asciiFoldingTokenFilter,
        shingleTokenFilter,
        englishStemmerTokenFilter,
        englishPossessiveStemmerTokenFilter),
      tokenizers = List(pathTokenizer)
    )

  }
}
