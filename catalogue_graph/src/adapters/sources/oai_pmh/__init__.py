"""Shared OAI-PMH adapter infrastructure.

This module provides generic OAI-PMH harvesting capabilities that can be
reused across multiple adapter implementations (Axiell, FOLIO, etc.).

Components:
- runtime: OAIPMHRuntimeConfig protocol for adapter configuration
- models: Generic event types for trigger/loader steps
- steps: Reusable trigger and loader implementations
- reporting: Base metrics and reporting classes
- record_writer: Generic record persistence callback

Auth Notes:
- Axiell uses a custom "Token" header for authentication
- FOLIO uses OAuth2 with an "Authorization: Bearer" header
- Auth strategies should be injected via the runtime config's build_oai_client()
"""
