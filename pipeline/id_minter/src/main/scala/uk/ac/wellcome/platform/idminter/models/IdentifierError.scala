package uk.ac.wellcome.platform.idminter.models

sealed trait IdentifierError extends Exception
case class IdentifierWriteError(e: Throwable) extends IdentifierError
case class IdentifierReadError(e: Throwable) extends IdentifierError
case class IdentifierAmbiguousError() extends IdentifierError
case class IdentifierDoesNotExistError() extends IdentifierError
