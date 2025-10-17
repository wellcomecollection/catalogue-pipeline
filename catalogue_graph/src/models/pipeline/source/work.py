from models.pipeline.work import DeletedWork, VisibleWork, Work
from models.pipeline.work_state import WorkState


class SourceWorkState(WorkState):
    pass

    def id(self) -> str:
        return str(self.source_identifier)


class SourceWork(Work):
    state: SourceWorkState


class VisibleSourceWork(VisibleWork, SourceWork):
    pass


class DeletedSourceWork(DeletedWork, SourceWork):
    pass
