from models.pipeline.serialisable import ElasticsearchModel

from .relation import DisplayRelation


class DisplayCollection(ElasticsearchModel):
    """Information about the collection hierarchy a work belongs to.

    Collection hierarchies come from collection paths, so they include hierarchies
    which are not archives (such as those derived from Sierra 773/774 fields).
    """

    root: DisplayRelation | None
    is_root: bool | None
