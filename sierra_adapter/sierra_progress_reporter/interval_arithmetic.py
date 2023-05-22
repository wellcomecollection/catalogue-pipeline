import datetime as dt
import json
import os

import attr


@attr.s(repr=False)
class Interval:
    start = attr.ib()
    end = attr.ib()
    key = attr.ib()

    def __repr__(self):
        return "%s(start=%r, end=%r, key=%r)" % (
            type(self).__name__,
            self.start.isoformat(),
            self.end.isoformat(),
            self.key,
        )

    __str__ = __repr__


def strip_timestamp(timestamp):
    # The timezone offset may or may not be present, remove it if it's there
    return timestamp.strip("Z").replace("+00-00", "")


def get_intervals(keys):
    """
    Generate the intervals completed for a particular resource type.

    :param keys: A generator of S3 key names.

    """
    for k in keys:
        name = os.path.basename(k)
        data = json.loads(name)

        start = dt.datetime.fromisoformat(data["start"]).replace(tzinfo=None)
        end = dt.datetime.fromisoformat(data["end"]).replace(tzinfo=None)

        yield Interval(start=start, end=end, key=k)


def combine_overlapping_intervals(sorted_intervals):
    """
    Given a generator of sorted open intervals, generate the covering set.
    It produces a series of 2-tuples: (interval, running), where ``running``
    is the set of sub-intervals used to build the overall interval.

    :param sorted_intervals: A generator of ``Interval`` instances.

    """
    lower = None
    running = []

    for higher in sorted_intervals:
        if not lower:
            lower = higher
            running.append(higher)
        else:
            # We treat these as open intervals.  This first case is for the
            # two intervals being wholly overlapping, for example:
            #
            #       ( -- lower -- )
            #               ( -- higher -- )
            #
            if higher.start < lower.end:
                upper_bound = max(lower.end, higher.end)
                lower = Interval(start=lower.start, end=upper_bound, key=None)
                running.append(higher)

            # Otherwise the two intervals are disjoint.  Note that this
            # includes the case where lower.end == higher.start, because
            # we can't be sure that point has been included.
            #
            #       ( -- lower -- )
            #                      ( -- higher -- )
            #
            # or
            #
            #       ( -- lower -- )
            #                           ( -- higher -- )
            #
            else:
                yield (lower, running)
                lower = higher
                running = [higher]

    # And spit out the final interval
    if lower is not None:
        yield (lower, running)
