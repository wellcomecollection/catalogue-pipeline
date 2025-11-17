def sort_notes(a, b):
    def sort_key(i):
        return i["noteType"]["id"]

    return sorted(a, key=sort_key), sorted(b, key=sort_key)


def nest_ancestors(a, b):
    if not b:
        return a, b

    adjusted_part_of = []
    recursive_part_of, curr_ancestor = None, None
    for item in b:
        if 'id' in item and item.get("totalParts", 0) > 0:
            if curr_ancestor is not None:
                curr_ancestor['partOf'] = [item]
            else:
                recursive_part_of = item
            curr_ancestor = item
        else:
            adjusted_part_of.append(item)

    if recursive_part_of is not None:
        adjusted_part_of.append(recursive_part_of)

    return a, adjusted_part_of
   
 
TRANSFORMS = {
    "display.notes": sort_notes,
    "display.partOf": nest_ancestors
}
