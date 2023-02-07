package weco.pipeline_storage

case class RetrieverMultiResult[T](
  found: Map[String, T],
  notFound: Map[String, Throwable]
) {
  require(
    found.keySet.intersect(notFound.keySet).isEmpty,
    s"The Retriever both found and did not find the same key: ${found.keySet
        .intersect(notFound.keySet)}. " +
      "This probably indicates a programming error."
  )

  def apply(id: String): Either[Throwable, T] =
    found
      .get(id)
      .map(Right(_))
      .orElse(notFound.get(id).map(Left(_)))
      .getOrElse(
        Left(new Exception(s"ID not found in RetrieverMultiResult: $id"))
      )
}
