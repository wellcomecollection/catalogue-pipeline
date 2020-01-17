val value = keywordField("value")
    .normalizer(lowercaseNormalizer.name)
    .fields(keywordField("raw"))

  val canonicalId =
    keywordField("canonicalId")
      .normalizer(lowercaseNormalizer.name)
      .fields(keywordField("raw"))