package weco.pipeline.transformer.marc_common.models

import scala.util.{Failure, Success, Try}

trait MarcFieldOps {
  implicit class FieldOps(field: MarcField) {

    def onlySubfieldWith(tag: String): Try[Option[MarcSubfield]] = {
      field.subfields.filter(_.tag == tag) match {
        case Seq(subfield) => Success(Some(subfield))
        case Nil           => Success(None)
        case _ =>
          Failure(new Exception(s"found multiple non-repeating subfield $tag"))
      }
    }

  }
}
