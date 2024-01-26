package weco.pipeline.matcher.models

final case class VersionExpectedConflictException(message: String) extends Exception(message)

case class VersionUnexpectedConflictException(e: Throwable) extends Exception(e.getMessage)

case object VersionUnexpectedConflictException {
  def apply(message: String): VersionUnexpectedConflictException =
    VersionUnexpectedConflictException(new IllegalStateException(message))
}
