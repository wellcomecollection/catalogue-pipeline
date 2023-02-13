package weco.catalogue.internal_model.identifiers

import weco.json.{TypedString, TypedStringOps}

/** A reference number is a special type of identifier.
  *
  * when [the identifier is] the reference we have written on the actual item,
  * they should be the referenceNumber
  *
  * Ie there is a number written in pencil on the old piece of paper and it is
  * XXXXX - that's the reference number, as it's what people will see when they
  * have the object in their hands
  *
  * See https://wellcome.slack.com/archives/C294K7D5M/p1620733997159000 for more
  */
class ReferenceNumber(val underlying: String)
    extends TypedString[ReferenceNumber]

object ReferenceNumber extends TypedStringOps[ReferenceNumber] {
  def apply(underlying: String): ReferenceNumber =
    new ReferenceNumber(underlying)
}
