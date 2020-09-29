package uk.ac.wellcome.models.work.internal

/** Holds relations for a particular work. An Option[List[_]] is used to
  * represent cases both where the work has no relations and where the
  * relations are not currently known.
  *
  * @param parts Children of the work
  * @param partOf Parents of the work
  * @param precededBy Siblings preceding the work
  * @param succeededBy Siblings following the work
  */
case class Relations[State <: WorkState](
  parts: Option[List[Work[State]]],
  partOf: Option[List[Work[State]]],
  precededBy: Option[List[Work[State]]],
  succeededBy: Option[List[Work[State]]],
)

object Relations {

  def unknown[State <: WorkState]: Relations[State] =
    Relations(
      parts = None,
      partOf = None,
      precededBy = None,
      succeededBy = None
    )

  def nil[State <: WorkState]: Relations[State] =
    Relations(
      parts = Some(Nil),
      partOf = Some(Nil),
      precededBy = Some(Nil),
      succeededBy = Some(Nil)
    )
}
