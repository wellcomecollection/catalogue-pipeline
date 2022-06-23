package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.analysis._

object WorksAnalysis {
  // This analyzer "keeps" the slash, by turning it into
  // `__` which isn't removed by the standard tokenizer
  val slashesCharFilter =
    MappingCharFilter("slashes_char_filter", mappings = Map("/" -> " __"))

  val asciiFoldingTokenFilter = AsciiFoldingTokenFilter(
    "asciifolding_token_filter",
    preserveOriginal = Some(true)
  )

  val pathAnalyzer = CustomAnalyzer(
    "path_hierarchy_analyzer",
    tokenizer = "path_hierarchy",
    charFilters = Nil
  )

  val shingleTokenFilter = ShingleTokenFilter(
    "shingle_token_filter",
    minShingleSize = Some(2),
    maxShingleSize = Some(4)
  )

  val englishStemmerTokenFilter =
    StemmerTokenFilter("english_stemmer", lang = "english")

  val englishPossessiveStemmerTokenFilter =
    StemmerTokenFilter(
      "english_possessive_stemmer",
      lang = "possessive_english"
    )

  val languages =
    List("arabic", "bengali", "french", "german", "hindi", "italian")

  val languageFiltersAndAnalyzers = languages.map(lang => {
    val name = s"${lang}_stemmer"
    (
      StemmerTokenFilter(name, lang = lang),
      CustomAnalyzer(
        s"${lang}_analyzer",
        tokenizer = "standard",
        tokenFilters = List(
          "lowercase",
          name
        ),
        charFilters = Nil
      )
    )
  })

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
      englishPossessiveStemmerTokenFilter.name
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

  val slashesAnalyzer =
    CustomAnalyzer(
      "slashes_analyzer",
      tokenizer = "standard",
      charFilters = List(slashesCharFilter.name),
      tokenFilters = List("lowercase", asciiFoldingTokenFilter.name)
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
        slashesAnalyzer
      ) ++ languageFiltersAndAnalyzers.map(_._2),
      tokenFilters = List(
        asciiFoldingTokenFilter,
        shingleTokenFilter,
        englishStemmerTokenFilter,
        englishPossessiveStemmerTokenFilter
      ) ++ languageFiltersAndAnalyzers.map(_._1),
      normalizers = List(lowercaseNormalizer),
      charFilters = List(slashesCharFilter)
    )
  }
}
