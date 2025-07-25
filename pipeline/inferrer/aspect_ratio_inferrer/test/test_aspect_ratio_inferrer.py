from unittest.mock import patch, AsyncMock
import main
from fastapi.testclient import TestClient


def test_import_main_works():
    """Test that main module can be imported."""
    assert main is not None
    assert hasattr(main, 'app')
    assert hasattr(main, 'healthcheck')


def test_landscape_aspect_ratio_endpoint():
    """Test the aspect ratio endpoint with a mocked landscape image."""
    # Create a mock image object with landscape dimensions
    class MockImage:
        def __init__(self, width, height):
            self.width = width
            self.height = height
    
    mock_image = MockImage(200, 100)  # 2:1 landscape
    
    with patch('main.get_image_from_url', new_callable=AsyncMock) as mock_get_image:
        mock_get_image.return_value = mock_image
        
        client = TestClient(main.app)
        response = client.get("/aspect-ratio/?query_url=http://example.com/image.jpg")
        
        assert response.status_code == 200
        data = response.json()
        assert "aspect_ratio" in data
        assert data["aspect_ratio"] == 2.0
        assert data["aspect_ratio"] > 1  # landscape


def test_square_aspect_ratio_endpoint():
    """Test the aspect ratio endpoint with a mocked square image."""
    # Create a mock image object with square dimensions
    class MockImage:
        def __init__(self, width, height):
            self.width = width
            self.height = height
    
    mock_image = MockImage(100, 100)  # 1:1 square
    
    with patch('main.get_image_from_url', new_callable=AsyncMock) as mock_get_image:
        mock_get_image.return_value = mock_image
        
        client = TestClient(main.app)
        response = client.get("/aspect-ratio/?query_url=http://example.com/square.jpg")
        
        assert response.status_code == 200
        data = response.json()
        assert "aspect_ratio" in data
        assert data["aspect_ratio"] == 1.0  # square


def test_portrait_aspect_ratio_endpoint():
    """Test the aspect ratio endpoint with a mocked portrait image."""
    # Create a mock image object with portrait dimensions
    class MockImage:
        def __init__(self, width, height):
            self.width = width
            self.height = height
    
    mock_image = MockImage(100, 200)  # 1:2 portrait
    
    with patch('main.get_image_from_url', new_callable=AsyncMock) as mock_get_image:
        mock_get_image.return_value = mock_image
        
        client = TestClient(main.app)
        response = client.get("/aspect-ratio/?query_url=http://example.com/portrait.jpg")
        
        assert response.status_code == 200
        data = response.json()
        assert "aspect_ratio" in data
        assert data["aspect_ratio"] == 0.5
        assert data["aspect_ratio"] < 1  # portrait


def test_aspect_ratio_endpoint_error_handling():
    """Test the aspect ratio endpoint error handling when image URL is invalid."""
    with patch('main.get_image_from_url', new_callable=AsyncMock) as mock_get_image:
        # Simulate ValueError from get_image_from_url
        mock_get_image.side_effect = ValueError("Invalid image URL")
        
        client = TestClient(main.app)
        response = client.get("/aspect-ratio/?query_url=http://invalid-url.com/bad.jpg")
        
        assert response.status_code == 404
        data = response.json()
        assert "detail" in data


def test_healthcheck_function_exists():
    """Test that the healthcheck function exists."""
    assert hasattr(main, "healthcheck")
    response = main.healthcheck()
    assert response == {"status": "healthy"}
