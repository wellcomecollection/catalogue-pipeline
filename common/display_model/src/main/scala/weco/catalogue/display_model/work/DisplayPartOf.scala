package weco.catalogue.display_model.work

import weco.catalogue.internal_model.work.Relation

object DisplayPartOf {

  /**
    * The partOf hierarchy of DisplayRelations is constructed from a flat list of Relation objects
    * coming from the record in the database.
    *
    * There are effectively two distinct behaviours involved here (which is what makes this necessary).
    *  1. There is a strict single-parent hierarchy defining an objects position within a Collection
    *     2. An object can also be in a Series.
    *
    * A Series is a flat, unordered bag of Works, represented by a relation with no ID.
    * A Work may be in zero or more Series and in zero or one hierarchical position within a Collection.
    *
    * @param flatAncestors a list of all the ancestors of a relation with any hierarchy represented as a list ordered from furthest to nearest.
    * @return The result of reconstituting flatAncestors into any hierarchy that exists, alongside its Series Relations
    */
  def apply(flatAncestors: List[Relation]): List[DisplayRelation] =
    flatAncestors.foldLeft(List.empty[DisplayRelation]) {
      case (List(), nearer) =>
        List(DisplayRelation(nearer))
      case (head :: tail, nearer: Relation) =>
        (head.id, nearer.id) match {
          // The existing head is not part of the hierarchy,
          // Prepend the new relation.
          case (None, _) =>
            List(DisplayRelation(nearer), head) ++ tail
          // Both the existing head and the new entry are in the hierarchy,
          // Build the hierarchy and that will become the head.
          case (Some(_), Some(_)) =>
            List(DisplayRelation(nearer).copy(partOf = Some(List(head)))) ++ tail
          // The existing head is part of the hierarchy, but the new one isn't.
          // Preserve the current head and insert the new one behind it.
          case (Some(_), None) =>
            List(head, DisplayRelation(nearer)) ++ tail
        }
    }

}
