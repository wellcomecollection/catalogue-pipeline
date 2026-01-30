"""Axiell adapter record writer.

Re-exports from the generic OAI-PMH record_writer module for backwards compatibility.
New code should use adapters.oai_pmh.record_writer directly.
"""

from adapters.oai_pmh.record_writer import WindowRecordWriter

__all__ = ["WindowRecordWriter"]
