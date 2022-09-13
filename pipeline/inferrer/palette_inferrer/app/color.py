import numpy as np

edge_length = (1 << 16) + 1
max_hue = 6 * edge_length
max_sat = 0xFFFF
max_val = 0xFF


def rgb_to_hsv(arr):
    """
    This is an implementation of the algorithm by Chernow, Alander & Bochko (2015):
    "Integer-based accurate conversion between RGB and HSV color spaces"

    It takes a numpy array of 8-bit unsigned integer RGB 3-element arrays and returns
    integer triples for HSV values. These have the range:

    - H: 0 - 393222
    - S: 0 - 65535  (0xFFFF)
    - V: 0 - 255    (0xFF

    It has been confirmed to be accurate to within 1e-05 of the values returned
    by colorsys.rgb_to_hsv for the entire input domain.
    """

    # broaden the array straight away to ensure we don't encounter overflows
    arr32 = arr.astype("uint32")
    arr_max = arr32.max(-1)
    arr_min = arr32.min(-1)
    delta = arr_max - arr_min

    # handle divide-by-zero errors gracefully
    with np.errstate(divide="ignore", invalid="ignore"):
        s = ((delta << 16) - 1) // arr_max
        s[delta == 0] = 0
        out = np.zeros_like(arr32)

        diff_0_1 = np.abs(arr32[..., 0] - arr32[..., 1])
        diff_0_2 = np.abs(arr32[..., 0] - arr32[..., 2])
        diff_1_2 = np.abs(arr32[..., 1] - arr32[..., 2])

        # red is max, blue is min
        idx = (arr32[..., 0] == arr_max) & (arr32[..., 2] == arr_min)
        out[idx, 0] = 1 + ((diff_1_2[idx] << 16) // delta[idx])

        # green is max, blue is min
        idx = (arr32[..., 1] == arr_max) & (arr32[..., 2] == arr_min)
        out[idx, 0] = (2 * edge_length) - (1 + ((diff_0_2[idx] << 16) // delta[idx]))

        # green is max, red is min
        idx = (arr32[..., 1] == arr_max) & (arr32[..., 0] == arr_min)
        out[idx, 0] = (2 * edge_length) + (1 + (((-diff_0_2[idx]) << 16) // delta[idx]))

        # blue is max, red is min
        idx = (arr32[..., 2] == arr_max) & (arr32[..., 0] == arr_min)
        out[idx, 0] = (4 * edge_length) - (1 + (((-diff_0_1[idx]) << 16) // delta[idx]))

        # blue is max, green is min
        idx = (arr32[..., 2] == arr_max) & (arr32[..., 1] == arr_min)
        out[idx, 0] = (4 * edge_length) + (1 + ((diff_0_1[idx] << 16) // delta[idx]))

        # red is max, green is min
        idx = (arr32[..., 0] == arr_max) & (arr32[..., 1] == arr_min)
        out[idx, 0] = (6 * edge_length) - (1 + (((-diff_1_2[idx]) << 16) // delta[idx]))

        out[..., 1] = s
        out[..., 2] = arr_max

    return out / [max_hue, max_sat, max_val]


def rgb_to_hex(r, g, b):
    return '%02x%02x%02x' % (r, g, b)
