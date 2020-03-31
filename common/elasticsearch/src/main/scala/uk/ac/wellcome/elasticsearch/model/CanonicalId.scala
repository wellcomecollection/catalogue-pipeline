package uk.ac.wellcome.elasticsearch.model

trait CanonicalId[T] {
  def canonicalId(t: T): String
}

trait Version[T] {
  def version(t: T): Int
}
