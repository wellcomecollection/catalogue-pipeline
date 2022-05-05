package weco.pipeline.path_concatenator

object PathOps {
  implicit class StringOps(path: String) {

    lazy val firstNode: String =
      path.splitAt(path.indexOf("/"))._1

    lazy val lastNode: String =
      path.splitAt(path.lastIndexOf("/") + 1)._2

  }
  def pathJoin(nodes: Seq[String]): String =
    nodes.mkString("/")
}
