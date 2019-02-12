#!/usr/bin/env python
# -*- encoding: utf-8

import pytest

from run_job import should_run_sbt_project
from sbt_dependency_tree import Repository


@pytest.fixture(scope="session")
def repo():
    yield Repository(".sbt_metadata")


@pytest.mark.parametrize('project_name, path, should_run_project', [
    ("id_minter", "snapshots/Makefile", False),
    ("id_minter", "pipeline/Makefile", True),
])
def test_should_run_sbt_project(repo, project_name, path, should_run_project):
    project = repo.get_project(project_name)
    result = should_run_sbt_project(project, changed_paths=[path])
    assert result == should_run_project
