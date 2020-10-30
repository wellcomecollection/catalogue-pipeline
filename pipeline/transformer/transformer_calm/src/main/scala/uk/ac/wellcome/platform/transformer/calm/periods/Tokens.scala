package uk.ac.wellcome.platform.transformer.calm.periods

sealed trait PeriodToken extends Product with Serializable

sealed trait ElementToken extends PeriodToken
case class Century(n: Int) extends ElementToken
case class Decade(n: Int) extends ElementToken
case class Year(n: Int) extends ElementToken
case class Month(str: String) extends ElementToken
case class Number(n: Int) extends ElementToken
case class Ordinal(n: Int) extends ElementToken
case object NoDate extends ElementToken
case object Present extends ElementToken
case class Season(season: String) extends ElementToken
case class LawTerm(term: String) extends ElementToken
case object Slash extends ElementToken
case object RangeSeparator extends ElementToken

sealed trait QualifierToken extends PeriodToken
case object Pre extends QualifierToken
case object Post extends QualifierToken
case object Mid extends QualifierToken
case object Early extends QualifierToken
case object Late extends QualifierToken
case object About extends QualifierToken
case object Approx extends QualifierToken
case object Between extends QualifierToken
case object Circa extends QualifierToken
case object Floruit extends QualifierToken
case class Era(era: String) extends QualifierToken
case object Gaps extends QualifierToken
