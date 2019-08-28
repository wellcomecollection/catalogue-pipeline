package uk.ac.wellcome.display.models

sealed trait WorkAgg
final case class WorkTypeAgg() extends WorkAgg

object WorksAggs {
  def apply(strs: Array[String]): List[WorkAgg] =
    (strs flatMap {
      case "workType" => Some(WorkTypeAgg())
      case _          => None
    }).toList
}
