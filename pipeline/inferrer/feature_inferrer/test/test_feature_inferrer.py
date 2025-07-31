import numpy as np
import base64
from unittest.mock import patch, AsyncMock
import main
from fastapi.testclient import TestClient


def test_import_main_works():
    """Test that main module can be imported."""
    assert main is not None
    assert hasattr(main, "app")
    assert hasattr(main, "healthcheck")


def test_feature_vector_endpoint():
    """Test the feature vector endpoint with mocked dependencies."""

    # Create a mock image object
    class MockImage:
        def __init__(self):
            self.width = 224
            self.height = 224

    mock_image = MockImage()

    # Mock feature vector from extract_features
    mock_features = np.array([0.1, 0.2, 0.3, 0.4], dtype=np.float32)

    with patch(
        "main.get_image_from_url", new_callable=AsyncMock
    ) as mock_get_image, patch(
        "main.batch_inferrer_queue.execute", new_callable=AsyncMock
    ) as mock_execute:
        mock_get_image.return_value = mock_image
        mock_execute.return_value = mock_features

        client = TestClient(main.app)
        response = client.get("/feature-vector/?query_url=http://example.com/image.jpg")

        assert response.status_code == 200
        data = response.json()
        assert "features_b64" in data

        # Decode the base64 features and verify normalization
        decoded_features = np.frombuffer(
            base64.b64decode(data["features_b64"]), dtype=np.float32
        )
        assert np.isclose(np.linalg.norm(decoded_features), 1.0)  # Should be normalized

        # Test the normalization logic directly with the same data
        # This matches the normalization in main.py: features / np.linalg.norm(features, axis=0, keepdims=True)
        normalized_features = mock_features / np.linalg.norm(
            mock_features, axis=0, keepdims=True
        )
        norm = np.linalg.norm(normalized_features)
        assert np.isclose(norm, 1.0)

        # Verify base64 encoding works
        encoded = base64.b64encode(normalized_features)
        assert isinstance(encoded, bytes)

        # Verify it can be decoded back
        decoded = np.frombuffer(base64.b64decode(encoded), dtype=np.float32)
        assert np.allclose(normalized_features, decoded)


def test_feature_vector_endpoint_error_handling():
    """Test the feature vector endpoint error handling when image URL is invalid."""
    with patch("main.get_image_from_url", new_callable=AsyncMock) as mock_get_image:
        # Simulate ValueError from get_image_from_url
        mock_get_image.side_effect = ValueError("Invalid image URL")

        client = TestClient(main.app)
        response = client.get(
            "/feature-vector/?query_url=http://invalid-url.com/bad.jpg"
        )

        assert response.status_code == 404
        data = response.json()
        assert "detail" in data


def test_feature_extraction_module_exists():
    """Test that feature extraction module exists."""
    from src.feature_extraction import extract_features

    assert extract_features is not None
    assert callable(extract_features)


def test_healthcheck_function_exists():
    """Test that the healthcheck function exists."""
    assert hasattr(main, "healthcheck")
    response = main.healthcheck()
    assert response == {"status": "healthy"}
