package weco.pipeline.transformer.mets.generators

import weco.fixtures.RandomGenerators

import scala.xml.{Elem, NodeSeq}

trait ArchivematicaMetsGenerators
    extends RandomGenerators
    with PremisAccessConditionsGenerators {

  def archivematicaMetsWith(
    rights: NodeSeq = openInCopyrightRightsMD
  ): Elem =
    <mets:mets xmlns:dv="http://dfg-viewer.de/" xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:premis="http://www.loc.gov/premis/v3" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" OBJID="276914" xsi:schemaLocation="http://www.loc.gov/standards/premis/ http://www.loc.gov/standards/premis/v2/premis-v2-0.xsd http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd http://www.loc.gov/METS/ http://www.loc.gov/standards/mets/mets.xsd http://www.loc.gov/standards/mix/ http://www.loc.gov/standards/mix/mix.xsd">
      <mets:amdSec ID="amdSec_1">
        {rights}
      </mets:amdSec>
    </mets:mets>
}
