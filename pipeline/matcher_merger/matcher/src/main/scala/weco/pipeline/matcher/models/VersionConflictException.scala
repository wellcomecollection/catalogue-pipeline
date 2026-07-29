package weco.pipeline.matcher.models

final case class VersionExpectedConflictException(message: String)
    extends Exception(message)
