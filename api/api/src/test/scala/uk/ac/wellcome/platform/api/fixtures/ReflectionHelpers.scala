package uk.ac.wellcome.platform.api.fixtures

import scala.reflect.runtime.universe._

trait ReflectionHelpers {
  // See https://stackoverflow.com/a/63303694/1558022
  def getFields[T: TypeTag]: Seq[String] =
    typeOf[T].members
      .collect {
        case m: MethodSymbol if m.isCaseAccessor => m.name.toString
      }
      .filterNot {
        _ == "_index"
      }
      .toSeq
      .map { _.replace("$u002E", ".") }
}
