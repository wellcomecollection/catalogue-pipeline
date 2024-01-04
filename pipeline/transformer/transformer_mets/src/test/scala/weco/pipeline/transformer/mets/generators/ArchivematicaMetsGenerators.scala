package weco.pipeline.transformer.mets.generators

import weco.fixtures.RandomGenerators

import scala.xml.{Elem, NodeSeq}

trait ArchivematicaMetsGenerators
    extends RandomGenerators
    with PremisAccessConditionsGenerators {

  def archivematicaMetsWith(
    rights: NodeSeq = openInCopyrightRightsMD,
    identifier: String = "some id"
  ): Elem =
    <mets:mets xmlns:dv="http://dfg-viewer.de/" xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:premis="http://www.loc.gov/premis/v3" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" OBJID="276914" xsi:schemaLocation="http://www.loc.gov/standards/premis/ http://www.loc.gov/standards/premis/v2/premis-v2-0.xsd http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd http://www.loc.gov/METS/ http://www.loc.gov/standards/mets/mets.xsd http://www.loc.gov/standards/mix/ http://www.loc.gov/standards/mix/mix.xsd">
      {dmdSecId(identifier)}
      <mets:amdSec ID="amdSec_1">
        {rights}
      </mets:amdSec>
    </mets:mets>

  def dmdSecId(identifier: String): Elem =
    <mets:dmdSec ID="dmdSec_2" CREATED="2023-08-11T17:33:14" STATUS="original">
    <mets:mdWrap MDTYPE="DC">
      <mets:xmlData>
        <dcterms:dublincore xmlns:dcterms="http://purl.org/dc/terms/"
                            xmlns:dc="http://purl.org/dc/elements/1.1/"
                            xsi:schemaLocation="http://purl.org/dc/terms/ https://dublincore.org/schemas/xmls/qdc/2008/02/11/dcterms.xsd">
          <dc:identifier>{identifier}</dc:identifier>
        </dcterms:dublincore>
      </mets:xmlData>
    </mets:mdWrap>
  </mets:dmdSec>

  lazy val archivematicaMetsWithMultipleIdentifiers =
    <mets:mets xmlns:dv="http://dfg-viewer.de/" xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:premis="http://www.loc.gov/premis/v3" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" OBJID="276914" xsi:schemaLocation="http://www.loc.gov/standards/premis/ http://www.loc.gov/standards/premis/v2/premis-v2-0.xsd http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd http://www.loc.gov/METS/ http://www.loc.gov/standards/mets/mets.xsd http://www.loc.gov/standards/mix/ http://www.loc.gov/standards/mix/mix.xsd">
      {dmdSecId("one ID")}
      {dmdSecId("another ID")}
    </mets:mets>

  lazy val archivematicaMetsWithNoRights =
    <mets:mets xmlns:dv="http://dfg-viewer.de/" xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:premis="http://www.loc.gov/premis/v3" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" OBJID="276914" xsi:schemaLocation="http://www.loc.gov/standards/premis/ http://www.loc.gov/standards/premis/v2/premis-v2-0.xsd http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd http://www.loc.gov/METS/ http://www.loc.gov/standards/mets/mets.xsd http://www.loc.gov/standards/mix/ http://www.loc.gov/standards/mix/mix.xsd">
      {dmdSecId("deadbeef")}
      <mets:amdSec ID="amdSec_1">
      </mets:amdSec>
    </mets:mets>

}
