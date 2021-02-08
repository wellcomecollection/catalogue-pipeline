package uk.ac.wellcome.models.work.internal

case class AccessCondition(
  status: Option[AccessStatus] = None,
  terms: Option[String] = None,
  to: Option[String] = None
) {
  def filterEmpty: Option[AccessCondition] =
    this match {
      case AccessCondition(None, None, None) => None
      case accessCondition                   => Some(accessCondition)
    }

  def hasRestrictions: Boolean = status.exists(_.hasRestrictions)
}

