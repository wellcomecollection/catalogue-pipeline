"""
Tests covering the update behaviour of the iceberg ebsco adapter
"""
from pytest import fail
import main


def test_noop():
    """
    When there are no updates to perform, nothing happens
    """
    fail()


def test_corrupt_input():
    """
    Given an update file that cannot be understood
    Then an Exception is raised
    """
    fail()


def test_new_table():
    """
    Given an environment with no data
    When an update is applied
    All the new data is stored
    :return:
    """


def test_update_records():
    """
    Given an existing iceberg table
    And an update file with the same records
    And some of those records are different
    When the update is applied
    Then the records will have been changed
    And the changed rows will be identifiably grouped by a changeset property
    """
    fail()


def test_inserst_records():
    """
    Given an existing iceberg table
    And an update file with the same records
    And some new records
    When the update is applied
    Then the new records will be added
    And the new rows will be identifiably grouped by a changeset property
    """
    fail()


def test_delete_records():
    """
    Given an existing iceberg table
    And an update file with some records missing
    When the update is applied
    Then the missing records will be marked as deleted
    And the content of those records will have been removed
    And the changed rows are identifiably grouped by a changeset property

    Deletion must be at least semi-soft, as deleted records are simply absent in the data from
    the supplier, but the pipeline model downstream of here operates by being told which records have changed.
    Those records are then consulted and an appropriate action taken.

    This also allows us to replay a deletion if something fails
    downstream of here.
    If the row is completely deleted, then we have no way of knowing what action to take in the ongoing pipeline.
    """
    fail()


def test_all_actions():
    """
    Given an existing Iceberg table
    And an update file with new, changed, absent and unchanged  records
    When the update is applied
    Then all the appropriate actions are taken
    And all the new, changed and deleted rows are identifiably grouped by a changeset property
    """
    fail()
