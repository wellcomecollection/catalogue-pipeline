from collections import Counter
import numpy as np
from sklearn.cluster import KMeans
from joblib import Parallel, delayed

from .color import rgb_to_hsv, max_hue, max_sat, max_val


class PaletteEncoder:
    def __init__(self, palette_weights, bin_sizes):
        """
        Instantiate a palette encoder that looks for len(palette_weights) colours,
        weighted by palette_weights in descending order of cluster cardinality,
        and quantises the resultant colors across bin_sizes bins, respectively.
        """
        self.palette_size = len(palette_weights)
        self.palette_weights = palette_weights
        self.bin_sizes = bin_sizes
        self.delayed_process_image = delayed(self.process_image)

    @staticmethod
    def hsv_ints_to_cartesian(hsv):
        hsv_scaled = hsv / [max_hue, max_sat, max_val]
        h_x = hsv_scaled[..., 1] * np.cos(hsv_scaled[..., 0])
        s_y = hsv_scaled[..., 1] * np.sin(hsv_scaled[..., 0])
        v_z = hsv_scaled[..., 2]
        return np.stack([h_x, s_y, v_z], axis=-1)

    @staticmethod
    def cartesian_to_hsv(cartesian):
        h = np.arctan2(cartesian[..., 1], cartesian[..., 0])
        s = np.linalg.norm(cartesian[..., 0:2], axis=-1)
        v = cartesian[..., 2]
        return np.stack(
            [h, s, v],
            axis=-1
        ).astype("uint32")

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
        distinct_cartesian_colors = PaletteEncoder.hsv_ints_to_cartesian(distinct_hsv_colors)
        clusters = KMeans(n_clusters=n).fit(
            distinct_cartesian_colors, sample_weight=distinct_color_freqs
        )

        # Sort clusters by the sum of the weights they contain
        label_weights = Counter()
        for label, weight in zip(clusters.labels_, distinct_color_freqs):
            label_weights[label] += weight
        labels_by_weight = [label for label, _ in label_weights.most_common()]

        # Unfortunately we need to make sure that the labels correspond to the centroids
        # because that behaviour is not guaranteed by sklearn.
        # https://github.com/scikit-learn/scikit-learn/blob/0fb307bf3/sklearn/cluster/_kmeans.py#L851
        centroid_labels = clusters.predict(clusters.cluster_centers_)
        consistently_indexed_centroids = clusters.cluster_centers_[
            np.argsort(centroid_labels)
        ]

        return PaletteEncoder.cartesian_to_hsv(
            consistently_indexed_centroids[labels_by_weight]
        )

    @staticmethod
    def get_bin_index(color, d):
        indices = np.floor(d * color / 256).astype(int)
        return indices[0] + d * indices[1] + d * d * indices[2]

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

    def process_image(self, image):
        """
        extract presence of colour in the image at multiple precision levels
        """
        colors = self.get_significant_colors(image, self.palette_size)
        combined_results = [
            (self.get_bin_index(color, n), n, weight)
            for n in self.bin_sizes
            for color, weight in zip(colors, self.palette_weights)
        ]

        return self.encode_for_elasticsearch(combined_results)

    def __call__(self, images):
        """
        process images in parallel
        """
        return Parallel(n_jobs=-2)(
            self.delayed_process_image(image) for image in images
        )
