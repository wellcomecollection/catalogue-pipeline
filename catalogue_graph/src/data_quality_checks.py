# Shim — real code is in graph/steps/data_quality_checks.py
from graph.steps.data_quality_checks import *  # noqa: F401, F403

if __name__ == "__main__":
    from graph.data_validation.concept_types import get_concepts_with_inconsistent_types
    from utils.logger import ExecutionContext, get_trace_id, setup_logging

    setup_logging(
        ExecutionContext(
            trace_id=get_trace_id(),
            pipeline_step="data_quality_checks",
        )
    )

    invalid_items = get_concepts_with_inconsistent_types()
    save_data_quality_check_result(list(invalid_items), "inconsistent_concept_types")  # noqa: F405
