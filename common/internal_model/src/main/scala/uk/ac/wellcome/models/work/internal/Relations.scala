package uk.ac.wellcome.models.work.internal

/** Holds relations for a particular work.
  *
  * @param ancestors Ancestors from root downwards
  * @param children Children of the work
  * @param siblingsPreceding Siblings preceding the work
  * @param siblingsSucceeding Siblings following the work
  */
case class Relations[State <: DataState](
  ancestors: List[Relation[State]],
  children: List[Relation[State]],
  siblingsPreceding: List[Relation[State]],
  siblingsSucceeding: List[Relation[State]],
)

object Relations {

  def none[State <: DataState]: Relations[State] =
    Relations(
      ancestors = Nil,
      children = Nil,
      siblingsPreceding = Nil,
      siblingsSucceeding = Nil
    )
}

/** A relation contains a particular related work
  *
  * @param data The work data
  * @param id The ID
  * @param depth The depth of the relation in the tree
  */
case class Relation[State <: DataState](
  data: WorkData[State],
  id: State#Id,
  depth: Int,
)
