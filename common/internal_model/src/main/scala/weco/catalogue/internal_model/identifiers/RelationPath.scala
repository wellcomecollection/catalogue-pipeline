package weco.catalogue.internal_model.identifiers

import weco.json.{TypedString, TypedStringOps}

/** A relation path describes how a work is related to other works.
  *
  * For example, we might have the following archive tree with relation paths:
  *
  *     PP
  *      └─ CRI               PP/CRI
  *           ├─ A            PP/CRI/A
  *           │  └─ 1         PP/CRI/A/1
  *           └─ B            PP/CRI/B
  *              ├─ 1         PP/CRI/B/1
  *              └─ 2         PP/CRI/B/2
  *
  * This allows us to identify that, for example, PP/CRI/B/1 is a direct child
  * of PP/CRI/B and a descendent of PP/CRI.
  *
  */
class RelationPath(val underlying: String)
    extends TypedString[ReferenceNumber] {

  def join(other: RelationPath): RelationPath =
    RelationPath(s"$underlying/${other.underlying}")
}

object RelationPath extends TypedStringOps[RelationPath] {
  def apply(underlying: String): RelationPath =
    new RelationPath(underlying)
}
