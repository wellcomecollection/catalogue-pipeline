package uk.ac.wellcome.elasticsearch
import com.sksamuel.elastic4s.analysis.{
  Analysis,
  CustomAnalyzer,
  CustomNormalizer,
  PathHierarchyTokenizer,
  ShingleTokenFilter,
  StemmerTokenFilter
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
    maxShingleSize = Some(4)
  )

  val englishStemmerTokenFilter =
    StemmerTokenFilter("english_token_filter", lang = "english")
  val englishPossessiveStemmerTokenFilter =
    StemmerTokenFilter(
      "english_possessive_token_filter",
      lang = "possessive_english"
    )
  val frenchStemmerTokenFilter =
    StemmerTokenFilter("french_token_filter", lang = "french")
  val italianStemmerTokenFilter =
    StemmerTokenFilter("italian_token_filter", lang = "italian")
  val germanStemmerTokenFilter =
    StemmerTokenFilter("german_token_filter", lang = "german")
  val hindiStemmerTokenFilter =
    StemmerTokenFilter("hindi_token_filter", lang = "hindi")
  val arabicStemmerTokenFilter =
    StemmerTokenFilter("arabic_token_filter", lang = "arabic")
  // val persianStemmerTokenFilter =
  //   StemmerTokenFilter("persian_token_filter", lang = "persian")
  val bengaliStemmerTokenFilter =
    StemmerTokenFilter("bengali_token_filter", lang = "bengali")

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
      englishStemmerTokenFilter.name,
      englishPossessiveStemmerTokenFilter.name
    ),
    charFilters = Nil
  )

  val frenchAnalyzer = CustomAnalyzer(
    "french_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      frenchStemmerTokenFilter.name
    ),
    charFilters = Nil
  )

  val italianAnalyzer = CustomAnalyzer(
    "italian_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      italianStemmerTokenFilter.name
    ),
    charFilters = Nil
  )
  val germanAnalyzer = CustomAnalyzer(
    "german_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      germanStemmerTokenFilter.name
    ),
    charFilters = Nil
  )
  val hindiAnalyzer = CustomAnalyzer(
    "hindi_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      hindiStemmerTokenFilter.name
    ),
    charFilters = Nil
  )
  val arabicAnalyzer = CustomAnalyzer(
    "arabic_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      arabicStemmerTokenFilter.name
    ),
    charFilters = Nil
  )
  // val persianAnalyzer = CustomAnalyzer(
  //   "persian_analyzer",
  //   tokenizer = "standard",
  //   tokenFilters = List(
  //     "lowercase",
  //     persianStemmerTokenFilter.name
  //   ),
  //   charFilters = Nil
  // )
  val bengaliAnalyzer = CustomAnalyzer(
    "bengali_analyzer",
    tokenizer = "standard",
    tokenFilters = List(
      "lowercase",
      bengaliStemmerTokenFilter.name
    ),
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
    charFilters = Nil
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
        frenchAnalyzer,
        italianAnalyzer,
        germanAnalyzer,
        hindiAnalyzer,
        arabicAnalyzer,
        // persianAnalyzer,
        bengaliAnalyzer,
        whitespaceAnalyzer
      ),
      tokenFilters = List(
        asciiFoldingTokenFilter,
        shingleTokenFilter,
        englishStemmerTokenFilter,
        englishPossessiveStemmerTokenFilter,
        frenchStemmerTokenFilter,
        italianStemmerTokenFilter,
        germanStemmerTokenFilter,
        hindiStemmerTokenFilter,
        arabicStemmerTokenFilter,
        // persianStemmerTokenFilter,
        bengaliStemmerTokenFilter
      ),
      tokenizers = List(pathTokenizer),
      normalizers = List(lowercaseNormalizer)
    )

  }
}
