package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[Id <: IdState]

case class SourceWorks[Id <: IdState, DataId <: IdState](
                                                  canonicalWork: SourceWork[Id, DataId],
                                                  redirectedWork: Option[SourceWork[Id, DataId]]
) extends ImageSource[Id]

case class SourceWork[Id <: WithSourceIdentifier, DataId <: IdState](
                                                 id: Id,
                                                 data: WorkData[DataId, Id],
                                                   ontologyType: String = "Work",
                                                 )
