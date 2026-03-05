# Shim — real code is in graph/steps/graph_status_poller.py
from graph.steps.graph_status_poller import *  # noqa: F401, F403

if __name__ == "__main__":
    import structlog

    from utils.logger import ExecutionContext, get_trace_id

    _logger = structlog.get_logger(__name__)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_status_poller",
    )
    result = handler(execution_context)  # noqa: F405
    _logger.info("Status poll complete", status=result["status"])
