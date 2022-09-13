from collections import Counter
import numpy as np
from sklearn.cluster import KMeans
from joblib import Parallel, delayed

from .color import rgb_to_hsv, rgb_to_hex

tau = 2 * np.pi
min_bin_width = 1.0 / 256


class PaletteEncoder:
    def __init__(
        self,
        palette_size,
        hue_bins,
        sat_bins,
        val_bins,
        sat_min,
        val_min,
        max_tokens=1000,
    ):
        """
        Instantiate a palette encoder that looks for palette_size colours,
        and quantises the resultant colors across <component>_bins, for each
        HSV component respectively (these must all be arrays of the same length).

        Saturations/values below sat_min and val_min (floats between 0 and 1) are binned
        separately. Images which would produce more than max_tokens will have their
        colour weights scaled down accordingly.
        """
        self.palette_size = palette_size
        self.bin_sizes = np.array([hue_bins, sat_bins, val_bins]).transpose()
        if self.bin_sizes.shape[0] != self.bin_sizes.shape[1]:
            raise Exception("All dimensions must have the same number of bins")

        self.bin_minima = np.array([0, sat_min, val_min])
        self.max_tokens = max_tokens
        self.delayed_process_image = delayed(self.process_image)

    def get_hash_params(self):
        return {
            "bin_sizes": [row.tolist() for row in self.bin_sizes.transpose()],
            "bin_minima": self.bin_minima.tolist(),
        }

    @staticmethod
    def hsv_to_cartesian(hsv):
        h_scaled = tau * hsv[..., 0]
        h_x = hsv[..., 1] * np.cos(h_scaled)
        s_y = hsv[..., 1] * np.sin(h_scaled)
        v_z = hsv[..., 2]
        return np.stack([h_x, s_y, v_z], axis=-1)

    @staticmethod
    def cartesian_to_hsv(cartesian):
        atan2 = np.arctan2(cartesian[..., 1], cartesian[..., 0])  # (-pi, pi]
        h = ((tau + atan2) % tau) / tau  # (0, 1]
        # The min(s, 1) is a guard against rounding errors here
        s = min(np.linalg.norm(cartesian[..., 0:2], axis=-1), 1.0)
        v = cartesian[..., 2]
        return np.stack([h, s, v], axis=-1)

    @staticmethod
    def get_rgb_pixels(image):
        rgb_image = image.convert("RGB")
        return np.array(rgb_image).reshape(-1, 3)

    @staticmethod
    def get_significant_colors(rgb_pixels, n):
        """
        extract n significant colours from the image pixels by taking the
        centres of n kmeans clusters of the image's pixels arranged in colour
        space. Returns colours in descending order of cluster cardinality.
        """
        hsv_pixels = rgb_to_hsv(rgb_pixels)

        # Only cluster distinct colours but weight them by their frequency
        # As per https://arxiv.org/pdf/1101.0395.pdf
        distinct_colors, distinct_color_freqs = np.unique(
            hsv_pixels, axis=0, return_counts=True
        )

        # Fewer distinct colours than clusters
        if len(distinct_colors) <= n:
            sort_indices = distinct_color_freqs.argsort()[::-1]
            sorted_by_freq = distinct_colors[sort_indices]
            size_diff = n - len(distinct_colors)
            # pad with the most frequently occurring distinct colour
            padded = np.pad(
                sorted_by_freq, pad_width=((size_diff, 0), (0, 0)), mode="edge"
            )
            padded_indices = np.pad(sort_indices, pad_width=(size_diff, 0), mode="edge")
            return zip(padded, distinct_color_freqs[padded_indices])

        distinct_cartesian_hsv_colors = PaletteEncoder.hsv_to_cartesian(distinct_colors)
        clusters = KMeans(n_clusters=n).fit(
            distinct_cartesian_hsv_colors, sample_weight=distinct_color_freqs
        )

        # Sort clusters by the sum of the weights they contain
        label_weights = Counter()
        for label, weight in zip(clusters.labels_, distinct_color_freqs):
            label_weights[label] += weight

        # Unfortunately we need to make sure that the labels correspond to the centroids
        # because that behaviour is not guaranteed by sklearn.
        # https://github.com/scikit-learn/scikit-learn/blob/0fb307bf3/sklearn/cluster/_kmeans.py#L851
        centroid_labels = clusters.predict(clusters.cluster_centers_)
        consistently_indexed_centroids = clusters.cluster_centers_[
            np.argsort(centroid_labels)
        ]

        return [
            (
                PaletteEncoder.cartesian_to_hsv(consistently_indexed_centroids[label]),
                weight,
            )
            for label, weight in label_weights.most_common()
        ]

    def get_bin_index(self, color, bin_sizes):
        s, v = color[1], color[2]
        n_val_bins = bin_sizes[1]
        sat_min, val_min = self.bin_minima[1], self.bin_minima[2]
        next_bin = 0

        # First bin is for very dark colours (effectively black)
        if v < val_min:
            return next_bin
        next_bin += 1

        # Next, bin values with low saturation irrespective of hue (shades of grey)
        if s < sat_min:
            return next_bin + np.floor(
                n_val_bins * (v - val_min) / (1.0 - val_min + min_bin_width)
            ).astype(int)
        next_bin += n_val_bins

        # Finally, bin the remainder of the space uniformly
        indices = np.floor(
            bin_sizes
            * (color - self.bin_minima)
            / (1.0 - self.bin_minima + min_bin_width)
        ).astype(int)
        return (
            next_bin
            + indices[0]
            + bin_sizes[0] * indices[1]
            + bin_sizes[0] * bin_sizes[1] * indices[2]
        )

    @staticmethod
    def encode_for_elasticsearch(values):
        """
        turn a list of bin indices into a list of strings for elasticsearch.
        weighting is performed by repeating elements `weight` times
        """
        return [
            f"{index}/{bin_i}" for index, bin_i, weight in values for _ in range(weight)
        ]

    def scale_weights(self, weights):
        total_weight = np.sum(weights) * len(self.bin_sizes)
        if total_weight > self.max_tokens:
            return np.round(self.max_tokens * (weights / total_weight)).astype("uint32")
        return weights

    def process_image(self, image):
        """
        extract presence of colour in the image at multiple precision levels
        """
        rgb_pixels = self.get_rgb_pixels(image)
        average_color_truncated = np.mean(rgb_pixels, axis=0).astype(np.int64)
        average_color_hex = rgb_to_hex(
            average_color_truncated[0],
            average_color_truncated[1],
            average_color_truncated[2],
        )

        colors, weights = zip(
            *self.get_significant_colors(rgb_pixels, self.palette_size)
        )
        scaled_weights = self.scale_weights(weights)
        combined_results = [
            (self.get_bin_index(color, bin_sizes), bin_i, weight)
            for bin_i, bin_sizes in enumerate(self.bin_sizes)
            for color, weight in zip(colors, scaled_weights)
        ]

        return {
            "lsh": self.encode_for_elasticsearch(combined_results),
            "average_color_hex": f"#{average_color_hex}",
        }

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
