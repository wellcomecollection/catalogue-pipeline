package uk.ac.wellcome.elasticsearch.model


trait CanonicalId[T]{
  def canonicalId(t: T): String
}
