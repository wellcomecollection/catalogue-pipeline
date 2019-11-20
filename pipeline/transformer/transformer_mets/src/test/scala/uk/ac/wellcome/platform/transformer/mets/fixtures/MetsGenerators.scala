package uk.ac.wellcome.platform.transformer.mets.fixtures

import uk.ac.wellcome.models.work.internal.License

trait MetsGenerators {
  def metsXmlWith(recordIdentifier: String, license: License) =
     <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       <mets:dmdSec ID="DMDLOG_0000">
         <mets:mdWrap MDTYPE="MODS">
           <mets:xmlData>
             <mods:mods>
               <mods:recordInfo>
                 <mods:recordIdentifier source="gbv-ppn">{recordIdentifier}</mods:recordIdentifier>
               </mods:recordInfo>
               <mods:accessCondition type="dz">{license.id.toUpperCase}</mods:accessCondition>
             </mods:mods>
           </mets:xmlData>
         </mets:mdWrap>
       </mets:dmdSec>
     </mets:mets>.toString()
}
