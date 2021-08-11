package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.source_model.sierra.SierraBibData
import weco.sierra.models.SierraQueryOps

// Populate wwork:lettering.
//
// We use the contents of MARC 246 ind .6 subfield $a, if present.
//
// Notes:
//
//  - We explicitly don't care about the first indicator.
//
//  - If a record is an archive, image or a journal, there are different
//    rules (Silver's notes say "look at 749 but not subfield 6").  We don't
//    have a way to distinguish these yet, so for now we don't do anything.
//    TODO: When we know how to identify these, implement the correct rules
//    for lettering.
//
//  - The MARC spec is unclear on whether there can be multiple instances
//    of 246 $a.  Field 246 is marked Repeatable, but $a is Non-Repeatable.
//    It's not clear if the NR is at field-level or record-level, so we
//    assume it may appear multiple times, and join with newlines.
//
//    TODO: Get a definite answer.
//
// https://www.loc.gov/marc/bibliographic/bd246.html
//
object SierraLettering extends SierraDataTransformer with SierraQueryOps {

  type Output = Option[String]

  def apply(bibData: SierraBibData) =
    bibData
      .varfieldsWithTag("246")
      .withIndicator2("6")
      .subfieldsWithTag("a")
      .contentString("\n\n")
}
