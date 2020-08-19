package uk.ac.wellcome.elasticsearch
import com.sksamuel.elastic4s.analysis.{
  Analysis,
  CustomAnalyzer,
  CustomNormalizer,
  PathHierarchyTokenizer,
  ShingleTokenFilter,
  StemmerTokenFilter,
}
import uk.ac.wellcome.elasticsearch.elastic4s.AsciiFoldingTokenFilter

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
    "asciifolding_analyzer",
    tokenizer = "standard",
    tokenFilters = List("lowercase", asciiFoldingTokenFilter.name),
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

  val whitespaceAnalyzer = CustomAnalyzer(
    "whitespace_analyzer",
    tokenizer = "whitespace",
    tokenFilters = Nil,
    charFilters = Nil,
  )

  val lowercaseNormalizer = CustomNormalizer(
    "lowercase_normalizer",
    tokenFilters = List("lowercase"),
    charFilters = Nil
  )

  def apply(): Analysis = {
    Analysis(
      analyzers = List(
        pathAnalyzer,
        asciifoldingAnalyzer,
        shingleAsciifoldingAnalyzer,
        englishAnalyzer,
        whitespaceAnalyzer,
      ),
      tokenFilters = List(
        asciiFoldingTokenFilter,
        shingleTokenFilter,
        englishStemmerTokenFilter,
        englishPossessiveStemmerTokenFilter),
      tokenizers = List(pathTokenizer),
      normalizers = List(lowercaseNormalizer)
    )

  }
}
