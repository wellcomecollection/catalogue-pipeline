package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Elem, NodeSeq}

object TeiNotes {

  def apply(xml: Elem): List[Note] =
    apply(xml \\ "msDesc" \ "msContents") ++ getDescLevelNotes(xml: Elem)

  def apply(node: NodeSeq): List[Note] =
    getLocus(node) ++ getColophon(node).toList ++ getIncipitAndExplicit(node) ++ getHandNotes(
      node)

  private def getDescLevelNotes(xml: Elem): Seq[Note] = {
    val msDesc = xml \\ "msDesc"
    getHandNotes(msDesc) ++ getHistory(msDesc)
  }

  /** The colophon is in `colophon` nodes under `msContents` or `msItem`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
    *   <teiHeader>
    *     <fileDesc>
    *       <sourceDesc>
    *         <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *           <msContents>
    *             <colophon> <locus>F. 9v</locus> </colophon>
    *
    */
  private def getColophon(value: NodeSeq): Seq[Note] =
    (value \ "colophon")
      .map { n =>
        n.text.trim
      }
      .flatMap { NormaliseText(_) }
      .filter { _.nonEmpty }
      .map { contents =>
        Note(contents = contents, noteType = NoteType.ColophonNote)
      }

  /** The `incipit` and `explicit` nodes are under `msItem` or `msPart`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="Wellcome_Alpha_932">
    *   <teiHeader>
    *     <fileDesc>
    *       <sourceDesc>
    *         <msDesc>
    *           <msContents>
    *             <msItem xml:id="Alpha_932_1">
    *               <incipit> <locus>F. 1v</locus>
    *                 <!-- transcript -->
    *                 oṃ namaḥ
    *                 japāpuṣyena saṃkāśaṃ kāśyapeyaṃ mahādyutiṃ
    *                 tam ahaṃ sarvapāpaghnaṃ praṇato smi divākaraṃ
    *                 sūryāya namaḥ
    *               </incipit>
    *               <explicit> <locus>F. 3r</locus>
    *                 <!-- transcript -->
    *                 ||12|| navagrahastotraṃ saṃpūraṇaṃ
    *               </explicit>
    *
    * The incipit/explicit are the first and last words of the text.
    * Normally they appear next to each other in the TEI; we create their notes
    * together so they'll appear together on wc.org.
    */
  private def getIncipitAndExplicit(value: NodeSeq): Seq[Note] =
    value
      .flatMap(_.nonEmptyChildren)
      .filter(n => n.label == "incipit" || n.label == "explicit")
      .map { n =>
        // The <locus> tag in an incipit/explicit tells us where this extract comes from;
        // so this is clear in the display prefix it with a colon.
        val locus = (n \ "locus").text

        val contents = if (locus.isEmpty) {
          n.text
        } else {
          n.text.replaceAll(s"$locus\\s*", s"$locus: ")
        }

        (n.label, NormaliseText(contents))
      }
      .collect {
        case ("incipit", Some(contents)) =>
          Note(contents = contents, noteType = NoteType.BeginsNote)
        case ("explicit", Some(contents)) =>
          Note(contents = contents, noteType = NoteType.EndsNote)
      }

  /**
    * Locus tags directly within msItem are extracted as
    * Locus notes and usually are used to tell what page the item begins on
    * and on what page it ends. They aren't necessarily the same as page numbers
    * in the locus tags in the incipit and explicit.
    * The locus tags that we extract as Locus notes are _only_ the locus tags directly under msItem as in:
    * <msItem xml:id="Wellcome_Malay_7_Part_1_Item_2">
    *  <locus>PP. 1-27.</locus>
    * </msItem>
    */
  private def getLocus(nodeSeq: NodeSeq): List[Note] =
    (nodeSeq \ "locus").flatMap { locus =>
      NormaliseText(locus.text.trim).map(Note(NoteType.LocusNote, _))
    }.toList

  /**
    * HandNotes contain information about how/who wrote the manuscript and are in the physical description node
    * which can be in msDesc for the wrapper work like:
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="Egyptian_MS_2">
    *  <teiHeader>
    *    <fileDesc>
    *      <sourceDesc>
    *        <msDesc>
    *          <physDesc>
    *            <handDesc>
    *              <handNote>Written in similar hand to the letters edited by Jac Janssen.</handNote>
    *            </handDesc>
    *          </physDesc>
    * or in msPart for a part:
    *<TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="Greek_14">
    * <teiHeader>
    *   <fileDesc>
    *     <sourceDesc>
    *       <msDesc>
    *         <msPart n="1" xml:id="Greek_14_A">
    *           <physDesc>
    *             <handDesc>
    *               <handNote scope="sole">identified by Agamemnon Tselikas as a fourteenth-century Cypriot hand.</handNote>
    *               </handDesc>
    *           </physDesc>
    *         </msPart>
    */
  def getHandNotes(nodeSeq: NodeSeq): List[Note] =
    (nodeSeq \ "physDesc" \ "handDesc" \ "handNote").flatMap { n =>
      //if the handNote has a scribe attribute, then it's being extracted as a contributor. No need to extract it again as a note
      if ((n \@ "scribe").isEmpty) {
        val label = n.text
        // scribes can also appear in handNote as <persName role="scr">someone</persName>.
        // Sometimes the persName tags are the only thing in the handNote tag and in that case, and in that case because
        // they are already extracted as contributors, there's no need to extract them again as notes.
        // Sometimes however, the persName nodes appear as part of a wider text in handNote. In that case we do
        // want to extract everything otherwise that text becomes unintelligible ie:
        // <handNote>In  neat handwriting by <persName role="scr">someone</persName></handNote>
        val labelNoScribes = n.child
          .filterNot(n => n.label == "persName" && (n \@ "role") == "scr")
          .text
          .trim
        if (labelNoScribes.nonEmpty) {
          NormaliseText(label).map(Note(NoteType.HandNote, _))
        } else {
          None
        }
      } else None
    }.toList

  /**
   * Extract the contents of `<history/>` that result in Notes.
   *
   *   https://tei-c.org/release/doc/tei-p5-doc/en/html/ref-history.html
   *
   *   - The two children of history that should result in notes are `<provenance/>` and `<acquisition/>`.
   *   - The value of `<origin/>` is extracted eleswhere (TeiProduction)
   *   - There are no examples of `history/summary` in the data.
   *
   */
  private def getHistory(nodeSeq: NodeSeq): List[Note] =
    (nodeSeq \ "history").flatMap { history =>
      getProvenance(history) ++ getAcquisition(history)
    }.toList

  private def getProvenance(nodeSeq: NodeSeq): List[Note] =
    (nodeSeq \ "provenance").flatMap { provenance =>
      TeiProvenanceNote(provenance.asInstanceOf[Elem])
    }.toList

  private def getAcquisition(nodeSeq: NodeSeq): List[Note] =
    (nodeSeq \ "acquisition").flatMap { acquisition =>
      TeiAcquisitionNote(acquisition.asInstanceOf[Elem])
    }.toList

}
