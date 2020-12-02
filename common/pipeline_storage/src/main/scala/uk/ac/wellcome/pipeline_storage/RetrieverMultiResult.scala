package uk.ac.wellcome.pipeline_storage

case class RetrieverMultiResult[T](
  found: Map[String, T],
  notFound: Map[String, Throwable]
) {
  require(found.keySet.intersect(notFound.keySet).isEmpty)
}
