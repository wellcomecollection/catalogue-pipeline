import structlog
from pymarc.record import Record

from adapters.marc.transformers.common import get_a_subfields

logger = structlog.get_logger(__name__)


def extract_predecessor_id(record: Record) -> str | None:
    """Extract the predecessor identifier from MARC 907 subfield $a."""
    pred_id = list(set(get_a_subfields("907", record)))
    if len(pred_id) > 1:
        logger.warning(
            "Multiple distinct instances of varfield with tag 907",
            count=len(pred_id),
        )
        raise ValueError("Multiple predecessor identifiers (907$a) found.")
    return pred_id[0].lstrip(".") if pred_id else None
