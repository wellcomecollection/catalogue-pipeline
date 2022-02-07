package weco.pipeline.id_minter.utils

import weco.catalogue.internal_model.identifiers.CanonicalId

import scala.util.Random

/**
  * Generate an easily shareable unique id
  *
  * The id is intended to be easily shareable in verbal or handwritten form, passing the "Post-it note test"
  * The generator is based on the following criteria:
  *
  * - There is no scope for ambiguity between handwritten characters (e.g. 0 vs o).
  * - It is short enough to read aloud or write down.
  * - The space is large enough that we don't have to worry about running out.
  * - It doesn't look like other kinds of ids, someone won't try to look it up in the wrong catalogue.
  */
object Identifiable {
  private val identifierLength = 8

  private val forbiddenLetters = List('o', 'i', 'l', '1')
  private val numberRange = '1' to '9'
  private val letterRange = 'a' to 'z'

  private val characterSet = numberRange ++ letterRange
  private val allowedCharacterSet =
    characterSet.filterNot(forbiddenLetters.contains)
  private val firstCharacterSet =
    allowedCharacterSet.filterNot(numberRange.contains)

  def generate: CanonicalId =
    CanonicalId(
      (1 to identifierLength).map {
        // One of the serialization formats of RDF is XML, so for
        // compatibility, our identifiers have to comply with XML rules.
        // XML identifiers cannot start with numbers, so we apply the same
        // rule to the identifiers we generate.
        //
        // See: http://stackoverflow.com/a/1077111/1558022
        case 1 => firstCharacterSet(Random.nextInt(firstCharacterSet.length))
        case _ =>
          allowedCharacterSet(Random.nextInt(allowedCharacterSet.length))
      }.mkString
    )
}
