package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Elem, NodeSeq}

object TeiNotes {
  def apply(xml: Elem): List[Note] =
    apply(xml \\ "msDesc" \ "msContents")

  def apply(node: NodeSeq): List[Note] =
    getColophon(node).toList ++ getIncipitAndExplicit(node) ++ getLocus(node)

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
   * The locus tags that we extract as Locus notes are _only_ the locust tags directly under msItem as in:
   * <msItem xml:id="Wellcome_Malay_7_Part_1_Item_2">
   *  <locus>PP. 1-27.</locus>
   * </msItem>
   */
  private def getLocus(nodeSeq: NodeSeq): Seq[Note] =
    (nodeSeq \ "locus").flatMap {locus =>
      NormaliseText(locus.text.trim).map(Note(NoteType.LocusNote, _))
    }
}
