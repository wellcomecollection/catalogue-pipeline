package weco.catalogue.internal_model.locations

import enumeratum.{Enum, EnumEntry}

sealed trait AccessMethod extends EnumEntry { this: AccessMethod =>
  def name: String = this.getClass.getSimpleName.stripSuffix("$")
}

object AccessMethod extends Enum[License] {
  val values = findValues
  assert(
    values.size == values.map { _.id }.toSet.size,
    "IDs for AccessMethod are not unique!"
  )

  // This is kept for compatibility with the 2021-08-09 index, but is no longer
  // set anywhere.  We should remove it after the next reindex.
  case object OpenShelves extends AccessMethod

  case object ViewOnline extends AccessMethod

  case object OnlineRequest extends AccessMethod
  case object ManualRequest extends AccessMethod

  case object NotRequestable extends AccessMethod
}
