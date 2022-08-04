package weco.pipeline.id_minter.database

import weco.catalogue.internal_model.identifiers.SourceIdentifier

case class SurplusIdentifierException(msg: String) extends RuntimeException(msg)

case object SurplusIdentifierException {
  def apply(sourceIdentifier: SourceIdentifier, distinctIdentifiers: Seq[SourceIdentifier]): SurplusIdentifierException = {
    val similarIdentifier = sourceIdentifier.copy(
      value = "^B".r.replaceAllIn(sourceIdentifier.value, "b")
    )
    val errorMessage =
      if (distinctIdentifiers.contains(similarIdentifier)) {
        s"""
           |The query returned a source identifier ($sourceIdentifier) which we weren't looking for,
           |but it did return a similar identifier ($similarIdentifier).
           |If this is a METS identifier, may have fixed the case of the b number in the source file;
           |if so, you'll need to update the associated records in the ID minter database.
           |See https://github.com/wellcomecollection/catalogue-pipeline/blob/main/pipeline/id_minter/connect_to_the_database.md
           |""".stripMargin
      } else {
        s"""
           |The query returned a sourceIdentifier ($sourceIdentifier) which we weren't looking for
           |($distinctIdentifiers)
           |""".stripMargin
      }

    new SurplusIdentifierException(
      errorMessage.replace("\n", " "))
  }
}
