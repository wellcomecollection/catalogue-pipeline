package weco.pipeline.sierra_reader.models

import weco.sierra.models.identifiers.UntypedSierraRecordNumber

case class WindowStatus(
  id: Option[UntypedSierraRecordNumber],
  offset: Int
)

case object WindowStatus {
  def apply(offset: Int): WindowStatus =
    WindowStatus(id = None, offset = offset)

  def apply(id: String, offset: Int): WindowStatus =
    WindowStatus(id = Some(UntypedSierraRecordNumber(id)), offset = offset)
}
