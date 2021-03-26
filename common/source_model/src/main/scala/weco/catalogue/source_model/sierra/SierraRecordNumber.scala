package weco.catalogue.source_model.sierra

object SierraRecordTypes extends Enumeration {
  val bibs, items, holdings = Value
}

trait SierraRecordNumber {
  val recordNumber: String

  if ("""^[0-9]{7}$""".r.unapplySeq(recordNumber) isEmpty) {
    throw new IllegalArgumentException(
      s"requirement failed: Not a 7-digit Sierra record number: $recordNumber"
    )
  }

  override def toString: String = withoutCheckDigit

  /** Returns the ID without the check digit or prefix. */
  def withoutCheckDigit: String = recordNumber
}

sealed trait TypedSierraRecordNumber extends SierraRecordNumber {
  val recordType: SierraRecordTypes.Value

  /** Returns the ID with the check digit and prefix. */
  def withCheckDigit: String = {
    val prefix = recordType match {
      case SierraRecordTypes.bibs     => "b"
      case SierraRecordTypes.items    => "i"
      case SierraRecordTypes.holdings => "h"
      case _ =>
        throw new RuntimeException(
          s"Received unrecognised record type: $recordType"
        )
    }

    s"$prefix$withoutCheckDigit$getCheckDigit"
  }

  /** Returns the check digit that should be added to a record ID.
    *
    * Quoting from the Sierra manual:
    *
    * Check digits may be any one of 11 possible digits (0, 1, 2, 3, 4,
    * 5, 6, 7, 8, 9, or x).
    *
    * The check digit is calculated as follows:
    *
    * Multiply the rightmost digit of the record number by 2, the next
    * digit to the left by 3, the next by 4, etc., and total the products.
    *
    * Divide the total by 11 and retain the remainder.  The remainder
    * after the division is the check digit.  If the remainder is 10,
    * the letter x is used as the check digit.
    *
    * See https://documentation.iii.com/sierrahelp/Default.htm#sril/sril_records_numbers.html
    *
    */
  private def getCheckDigit: String = {
    val remainder = recordNumber.reverse
      .zip(Stream from 2)
      .map { case (char: Char, count: Int) => char.toString.toInt * count }
      .sum % 11
    if (remainder == 10) "x" else remainder.toString
  }

  // Normally we use case classes for immutable data, which provide these
  // methods for us.
  //
  // We deliberately don't use case classes here so we skip automatic
  // case class derivation for JSON encoding (see below), so we have to
  // define our own comparison methods.
  def canEqual(a: Any): Boolean = a.isInstanceOf[TypedSierraRecordNumber]

  override def equals(that: Any): Boolean =
    that match {
      case that: TypedSierraRecordNumber =>
        that.canEqual(this) && this.withCheckDigit == that.withCheckDigit
      case _ => false
    }

  override def hashCode: Int = this.withCheckDigit.hashCode
}

// Note: these are deliberately classes rather than case classes so that
// we can have fine-grained control over how their encoding/decoding works.
//
// We have a mixture of at least three different JSON encodings in the pipeline:
//
//  - as a String
//  - as an Int
//  - as a JSON object {"recordNumber": "1234567"}
//
// We have a decoder that will handle all three, but if these were case classes
// we might get the automatically derived Circe encoder.  Making these regular
// classes will force us to supply our special decoder.

class UntypedSierraRecordNumber(val recordNumber: String)
    extends SierraRecordNumber

object UntypedSierraRecordNumber {
  def apply(number: String) = new UntypedSierraRecordNumber(number)
}

class SierraBibNumber(val recordNumber: String)
    extends TypedSierraRecordNumber {
  val recordType: SierraRecordTypes.Value = SierraRecordTypes.bibs
}

object SierraBibNumber {
  def apply(number: String) = new SierraBibNumber(number)
}

class SierraItemNumber(val recordNumber: String)
    extends TypedSierraRecordNumber {
  val recordType: SierraRecordTypes.Value = SierraRecordTypes.items
}

object SierraItemNumber {
  def apply(number: String) = new SierraItemNumber(number)
}

class SierraHoldingsNumber(val recordNumber: String)
    extends TypedSierraRecordNumber {
  val recordType: SierraRecordTypes.Value = SierraRecordTypes.holdings
}

object SierraHoldingsNumber {
  def apply(number: String) = new SierraHoldingsNumber(number)
}
