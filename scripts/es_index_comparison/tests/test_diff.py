from __future__ import annotations

from es_index_comparison.diff import compile_ignore_patterns, deep_diff, sort_lists


def diff_unordered(left, right, patterns):
    unordered = compile_ignore_patterns(patterns)
    return deep_diff(sort_lists(left, unordered), sort_lists(right, unordered))


def test_list_order_is_a_difference_by_default():
    assert deep_diff({"tags": ["a", "b"]}, {"tags": ["b", "a"]})


def test_only_matching_lists_ignore_order():
    left = {"tags": ["a", "b"], "steps": [1, 2]}
    right = {"tags": ["b", "a"], "steps": [2, 1]}

    diffs = diff_unordered(left, right, ["tags"])

    assert {d["path"] for d in diffs} == {"steps[0]", "steps[1]"}


def test_lists_nested_in_a_matching_list_ignore_order():
    left = {"items": [{"id": 1, "notes": ["x", "y"]}, {"id": 2, "notes": []}]}
    right = {"items": [{"id": 2, "notes": []}, {"id": 1, "notes": ["y", "x"]}]}

    assert diff_unordered(left, right, ["items"]) == []


def test_pattern_can_target_only_the_nested_list():
    left = {"items": [{"id": 1, "notes": ["x", "y"]}, {"id": 2, "notes": []}]}
    right = {"items": [{"id": 1, "notes": ["y", "x"]}, {"id": 2, "notes": []}]}

    assert diff_unordered(left, right, ["items[].notes"]) == []
    assert diff_unordered(left, {"items": right["items"][::-1]}, ["items[].notes"])


def test_double_star_matches_every_list():
    left = {"a": {"tags": ["a", "b"]}, "steps": [1, 2]}
    right = {"a": {"tags": ["b", "a"]}, "steps": [2, 1]}

    assert diff_unordered(left, right, ["**"]) == []


def test_unordered_lists_still_report_a_changed_item():
    left = {"items": [{"id": 1}, {"id": 2}]}
    right = {"items": [{"id": 3}, {"id": 1}]}

    diffs = diff_unordered(left, right, ["items"])

    assert [(d["path"], d["left"], d["right"]) for d in diffs] == [("items[1].id", 2, 3)]
