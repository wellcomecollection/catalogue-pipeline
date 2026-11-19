from __future__ import annotations

from es_index_comparison.diff import compile_ignore_patterns, deep_diff


def diff(left, right, unordered=()):
    diffs = deep_diff(left, right, compile_ignore_patterns(list(unordered)))
    return [(d["type"], d["path"], d["left"], d["right"]) for d in diffs]


def test_scalar_key_and_type_differences():
    left = {"title": "A", "count": 1, "old": True}
    right = {"title": "B", "count": "1", "new": [1]}

    assert diff(left, right) == [
        ("type_changes", "count", 1, "1"),
        ("dictionary_item_added", "new", None, [1]),
        ("dictionary_item_removed", "old", True, None),
        ("values_changed", "title", "A", "B"),
    ]


def test_null_and_missing_key_are_equal():
    assert diff({"items": [{"id": 1, "note": None}]}, {"items": [{"id": 1}]}) == []


def test_list_order_is_a_difference_by_default():
    assert diff({"tags": ["a", "b"]}, {"tags": ["b", "a"]})


def test_only_matching_lists_ignore_order():
    left = {"tags": ["a", "b"], "steps": [1, 2]}
    right = {"tags": ["b", "a"], "steps": [2, 1]}

    assert {path for _, path, _, _ in diff(left, right, ["tags"])} == {"steps[0]", "steps[1]"}


def test_lists_nested_in_a_matching_list_ignore_order():
    left = {"items": [{"id": 1, "notes": ["x", "y"]}, {"id": 2, "notes": []}]}
    right = {"items": [{"id": 2, "notes": []}, {"id": 1, "notes": ["y", "x"]}]}

    assert diff(left, right, ["items"]) == []


def test_pattern_can_target_only_the_nested_list():
    left = {"items": [{"id": 1, "notes": ["x", "y"]}, {"id": 2, "notes": []}]}
    right = {"items": [{"id": 1, "notes": ["y", "x"]}, {"id": 2, "notes": []}]}

    assert diff(left, right, ["items[].notes"]) == []
    assert diff(left, {"items": right["items"][::-1]}, ["items[].notes"])


def test_an_added_item_does_not_change_how_the_others_are_compared():
    left = {"items": [{"id": "b", "label": "B"}, {"id": "c", "label": "C"}]}
    right = {"items": [{"id": "a", "label": "A"}, *left["items"]]}

    assert diff(left, right, ["items"]) == [
        ("iterable_item_added", "items[0]", None, {"id": "a", "label": "A"})
    ]


def test_a_slightly_changed_item_is_paired_with_its_counterpart():
    left = {"items": [{"id": 1, "label": "a", "note": "x"}, {"id": 2, "label": "b", "note": "y"}]}
    right = {
        "items": [
            {"id": 9, "label": "z", "note": "w"},
            {"id": 2, "label": "B", "note": "y"},
            {"id": 1, "label": "a", "note": "x"},
        ]
    }

    assert diff(left, right, ["items"]) == [
        ("iterable_item_added", "items[0]", None, {"id": 9, "label": "z", "note": "w"}),
        ("values_changed", "items[1].label", "b", "B"),
    ]


def test_a_repeated_item_in_an_unordered_list_is_reported_with_its_counts():
    assert diff({"tags": ["a", "a", "b"]}, {"tags": ["b", "a"]}, ["tags"]) == [
        ("repetition_change", "tags[0]", 2, 1)
    ]


def test_ignored_paths_are_left_out_of_the_comparison():
    left = {"version": 1, "items": [{"id": 1, "seen": "a"}], "tags": ["x", "y"]}
    right = {"version": 2, "items": [{"id": 1, "seen": "b"}], "tags": ["x", "z"]}
    ignore = compile_ignore_patterns(["version", "items[].seen", "tags[]"])

    assert deep_diff(left, right, ignore=ignore) == []
    assert deep_diff(left, right, ignore=ignore[:2])
