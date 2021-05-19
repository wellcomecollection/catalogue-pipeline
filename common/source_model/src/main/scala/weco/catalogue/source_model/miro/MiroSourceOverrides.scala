package weco.catalogue.source_model.miro

import weco.catalogue.internal_model.locations.License

case class MiroSourceOverrides(
  license: Option[License]
)

object MiroSourceOverrides {
  def empty: MiroSourceOverrides =
    MiroSourceOverrides(
      license = None
    )
}
