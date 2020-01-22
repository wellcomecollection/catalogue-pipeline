package uk.ac.wellcome.models.work.internal

case class Collection(
  label: Option[String],
  path: String,
) {
  val paths: List[String] =
    path.split("/").inits.toList.reverse.drop(0).map(_.mkString("/"))
  val depth: Int = paths.size
}
