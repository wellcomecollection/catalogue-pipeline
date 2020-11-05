package uk.ac.wellcome.platform.transformer.calm.periods

sealed trait Qualifier

object Qualifier {
  case object Pre extends Qualifier
  case object Post extends Qualifier
  case object Mid extends Qualifier
  case object Early extends Qualifier
  case object Late extends Qualifier
  case object About extends Qualifier
  case object Approx extends Qualifier
  case object Between extends Qualifier
  case object Circa extends Qualifier
  case object Floruit extends Qualifier
  case class Era(era: String) extends Qualifier
  case object Gaps extends Qualifier
}
