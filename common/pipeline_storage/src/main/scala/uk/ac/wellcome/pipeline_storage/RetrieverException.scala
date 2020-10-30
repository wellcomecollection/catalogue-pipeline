package uk.ac.wellcome.pipeline_storage

class RetrieverException(message: String) extends RuntimeException(message)

class RetrieverNotFoundException(message: String) extends RetrieverException(message)

object RetrieverNotFoundException {
  def id(id: String): RetrieverNotFoundException =
    new RetrieverNotFoundException(s"Nothing found with ID $id!")

  def ids(ids: Seq[String]): RetrieverNotFoundException =
    new RetrieverNotFoundException(s"Nothing found with ID(s) ${ids.mkString(", ")}!")
}
