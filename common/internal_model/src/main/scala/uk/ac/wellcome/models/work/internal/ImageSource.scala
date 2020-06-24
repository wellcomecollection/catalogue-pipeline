package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[Id <: WithSourceIdentifier, DataId <: IdState]

case class SourceWorks[Id <: WithSourceIdentifier, DataId <: IdState](
                                                  canonicalWork: SourceWork[Id, DataId]
                                                 , redirectedWork: Option[SourceWork[Id, DataId]]
) extends ImageSource[Id, DataId]

case class SourceWork[Id <: WithSourceIdentifier, DataId <: IdState](
                                                 id: Id,
                                               data: WorkData[DataId, Id],
                                                   ontologyType: String = "Work",
                                                 )
