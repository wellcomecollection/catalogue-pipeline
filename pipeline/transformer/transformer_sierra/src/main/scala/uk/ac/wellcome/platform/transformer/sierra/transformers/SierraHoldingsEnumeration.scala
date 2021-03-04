package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.VarField

// The 85X/86X pairs are used to store structured captions -- the 85X contains
// the labels, the 856X contains the values.
//
// e.g. if you had the pair:
//
//    853 00 |810|avol.|i(year)
//    863 40 |810.1|a1|i1995
//
// then you combine the label/captions to get "vol.1 (1995)".
//
// The behaviour of this class is partly based on the published descriptions,
// partly on the observed behaviour on the old wellcomelibrary.org website.
object SierraHoldingsEnumeration {
  val labelTag = "853"
  val valueTag = "863"

  def apply(varFields: List[VarField]): List[String] =
    List()
}
