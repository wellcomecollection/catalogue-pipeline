package uk.ac.wellcome.models.work.internal

import java.time.Instant

import WorkState.{Indexed, Identified}

/** Holds relations for a particular work.
  *
  * @param ancestors Ancestors from root downwards
  * @param children Children of the work
  * @param siblingsPreceding Siblings preceding the work
  * @param siblingsSucceeding Siblings following the work
  */
case class Relations[State <: DataState](
  ancestors: List[Relation[State]] = Nil,
  children: List[Relation[State]] = Nil,
  siblingsPreceding: List[Relation[State]] = Nil,
  siblingsSucceeding: List[Relation[State]] = Nil,
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
  numChildren: Int,
  numDescendents: Int,
)

object Relation {

  def apply(work: Work[Identified],
            depth: Int,
            numChildren: Int,
            numDescendents: Int): Relation[DataState.Identified] =
    Relation[DataState.Identified](
      data = work.data,
      id = IdState.Identified(
        sourceIdentifier = work.state.sourceIdentifier,
        canonicalId = work.state.canonicalId
      ),
      depth = depth,
      numChildren = numChildren,
      numDescendents = numDescendents,
    )

  def fromIndexedWork(work: Work[Indexed],
                      depth: Int,
                      numChildren: Int,
                      numDescendents: Int): Relation[DataState.Identified] =
    Relation[DataState.Identified](
      data = work.data,
      id = IdState.Identified(
        canonicalId = work.state.canonicalId,
        sourceIdentifier = work.state.sourceIdentifier
      ),
      depth = depth,
      numChildren = numChildren,
      numDescendents = numDescendents
    )

  implicit class ToWork(relation: Relation[DataState.Identified]) {
    def toWork: Work.Visible[Indexed] =
      // NOTE: The numberOfSources, modifiedTime and version are not currently
      // stored for related works, so there is no way to recover them here.
      // Here we just initialise them to default values, which should not be an
      // issue (at least for now), due to the API not serialising them.
      Work.Visible[Indexed](
        data = relation.data,
        version = 1,
        state = Indexed(
          canonicalId = relation.id.canonicalId,
          sourceIdentifier = relation.id.sourceIdentifier,
          derivedData = DerivedData(relation.data),
          modifiedTime = Instant.ofEpochSecond(0)
        ),
      )
  }
}
