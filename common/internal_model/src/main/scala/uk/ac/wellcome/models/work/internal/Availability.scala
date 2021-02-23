package uk.ac.wellcome.models.work.internal

sealed trait Availability {
  val id: String
  val label: String
}

object Availability {
  case object Online extends Availability {
    val id = "online"
    val label = "Online"
  }

  case object InLibrary extends Availability {
    val id = "in-library"
    val label = "In the library"
  }
}
