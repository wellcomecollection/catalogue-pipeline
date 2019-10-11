package uk.ac.wellcome.platform.api.models

case class Error(
  errorType: String,
  httpStatus: Option[Int] = None,
  label: String,
  description: Option[String] = None
) {
  val ontologyType: String = "Error"
}

case object Error {
  def apply(variant: ErrorVariant, description: Option[String]): Error =
    Error(
      errorType = "http",
      httpStatus = Some(variant.status),
      label = variant.label,
      description = description
    )
}

sealed trait ErrorVariant {
  val status: Int
  val label: String
}

object ErrorVariant {

  val http400 = new ErrorVariant {
    val status = 400
    val label = "Bad Request"
  }

  val http404 = new ErrorVariant {
    val status = 404
    val label = "Not Found"
  }

  val http410 = new ErrorVariant {
    val status = 410
    val label = "Gone"
  }

  val http500 = new ErrorVariant {
    val status = 500
    val label = "Internal Server Error"
  }
}
