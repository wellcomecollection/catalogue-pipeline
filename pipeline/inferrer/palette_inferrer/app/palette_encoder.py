from collections import Counter
import numpy as np
from sklearn.cluster import KMeans
from joblib import Parallel, delayed

from .color import rgb_to_hsv, max_hue, max_sat, max_val


class PaletteEncoder:
    def __init__(
        self, palette_size, bin_sizes, sat_min, val_min, max_tokens=1000
    ):
        """
        Instantiate a palette encoder that looks for len(palette_weights) colours,
        weighted by palette_weights in descending order of cluster cardinality,
        and quantises the resultant colors across bin_sizes bins, respectively.
        """
        self.palette_size = palette_size
        self.bin_sizes = bin_sizes
        self.bin_minima = np.array([0.0, sat_min, val_min])
        self.max_tokens = max_tokens
        self.delayed_process_image = delayed(self.process_image)

    @staticmethod
    def hsv_ints_to_cartesian(hsv):
        hsv_scaled = hsv / [max_hue, max_sat, max_val]
        h_x = hsv_scaled[..., 1] * np.cos(hsv_scaled[..., 0])
        s_y = hsv_scaled[..., 1] * np.sin(hsv_scaled[..., 0])
        v_z = hsv_scaled[..., 2]
        return np.stack([h_x, s_y, v_z], axis=-1)

    @staticmethod
    def cartesian_to_hsv_floats(cartesian):
        h = np.arctan2(cartesian[..., 1], cartesian[..., 0])
        s = np.linalg.norm(cartesian[..., 0:2], axis=-1)
        v = cartesian[..., 2]
        return np.stack([h, s, v], axis=-1)

    @staticmethod
    def get_significant_colors(image, n):
        """
        extract n significant colours from the image pixels by taking the
        centres of n kmeans clusters of the image's pixels arranged in colour
        space. Returns colours in descending order of cluster cardinality.
        """
        rgb_image = image.convert("RGB")  # Should be RGB already but make sure
        rgb_pixels = np.array(rgb_image).reshape(-1, 3)

        # Only cluster distinct colours but weight them by their frequency
        # As per https://arxiv.org/pdf/1101.0395.pdf
        distinct_rgb_colors, distinct_color_freqs = np.unique(
            rgb_pixels, axis=0, return_counts=True
        )

        # Fewer distinct colours than clusters
        if len(distinct_rgb_colors) <= n:
            sort_indices = distinct_color_freqs.argsort()[::-1]
            sorted_by_freq = distinct_rgb_colors[sort_indices]
            size_diff = n - len(distinct_rgb_colors)
            # pad with the most frequently occurring distinct colour
            return np.pad(
                sorted_by_freq, pad_width=((size_diff, 0), (0, 0)), mode="edge"
            )

        distinct_hsv_colors = rgb_to_hsv(distinct_rgb_colors)
        distinct_cartesian_hsv_colors = PaletteEncoder.hsv_ints_to_cartesian(
            distinct_hsv_colors
        )
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
                PaletteEncoder.cartesian_to_hsv_floats(
                    consistently_indexed_centroids[label]
                ),
                weight,
            )
            for label, weight in label_weights.most_common()
        ]

    def get_bin_index(self, color, n_bins):
        h, s, v = color[0], color[1], color[2]
        sat_min, val_min = self.bin_minima[1], self.bin_minima[2]
        next_bin = 0

        # First bin is for very dark colours (effectively black)
        if v < val_min:
            return next_bin
        next_bin += 1

        # Next, bin values with low saturation irrespective of hue (shades of grey)
        if s < sat_min:
            return next_bin + np.floor(n_bins * (v - val_min)).astype(int)
        next_bin += n_bins

        # Finally, bin the remainder of the space uniformly
        indices = np.floor(n_bins * (color - self.bin_minima)).astype(int)
        return (
            next_bin + indices[0] + n_bins * indices[1] + n_bins * n_bins * indices[2]
        )

    @staticmethod
    def encode_for_elasticsearch(values):
        """
        turn a list of bin indices into a list of strings for elasticsearch.
        weighting is performed by repeating elements `weight` times
        """
        return [
            f"{index}/{n_bins}"
            for index, n_bins, weight in values
            for _ in range(weight)
        ]

    def scale_weights(self, weights):
        total_weight = np.sum(weights) * len(self.bin_sizes)
        if total_weight > self.max_tokens:
            return np.round(
                self.max_tokens * (weights / total_weight)
            ).astype("uint32")
        return weights

    def process_image(self, image):
        """
        extract presence of colour in the image at multiple precision levels
        """
        colors, weights = zip(*self.get_significant_colors(image, self.palette_size))
        scaled_weights = self.scale_weights(weights)
        combined_results = [
            (self.get_bin_index(color, n), n, weight)
            for n in self.bin_sizes
            for color, weight in zip(colors, scaled_weights)
        ]

        return self.encode_for_elasticsearch(combined_results)

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
