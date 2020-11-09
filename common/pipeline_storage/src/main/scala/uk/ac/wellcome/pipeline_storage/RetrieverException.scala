package uk.ac.wellcome.pipeline_storage

class RetrieverException(message: String) extends RuntimeException(message)

class RetrieverNotFoundException(id: String)
    extends RetrieverException(s"Nothing found with ID $id!")
