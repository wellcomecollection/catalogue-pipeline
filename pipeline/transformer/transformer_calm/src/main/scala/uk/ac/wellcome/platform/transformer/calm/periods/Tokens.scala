package uk.ac.wellcome.platform.transformer.calm.periods

sealed trait PeriodToken

sealed trait ElementToken extends PeriodToken
case class CENTURY(n: Int) extends ElementToken
case class DECADE(n: Int) extends ElementToken
case class YEAR(n: Int) extends ElementToken
case class MONTH(str: String) extends ElementToken
case class NUMBER(n: Int) extends ElementToken
case class ORDINAL(n: Int) extends ElementToken
case object NODATE extends ElementToken
case object PRESENT extends ElementToken
case class SEASON(season: String) extends ElementToken
case class LAWTERM(term: String) extends ElementToken
case object SLASH extends ElementToken

sealed trait QualifierToken extends PeriodToken
object QUALIFIER {
  case object PRE extends QualifierToken
  case object POST extends QualifierToken
  case object MID extends QualifierToken
  case object EARLY extends QualifierToken
  case object LATE extends QualifierToken
  case object ABOUT extends QualifierToken
  case object APPROX extends QualifierToken
  case object BETWEEN extends QualifierToken
  case object CIRCA extends QualifierToken
  case object FLORUIT extends QualifierToken
  case class ERA(era: String) extends QualifierToken
  case object GAPS extends QualifierToken
  case object RANGESEPARATOR extends QualifierToken
}
