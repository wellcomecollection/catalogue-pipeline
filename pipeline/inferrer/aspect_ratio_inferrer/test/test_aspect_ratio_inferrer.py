from unittest.mock import patch, AsyncMock
import main
from fastapi.testclient import TestClient


def test_import_main_works():
    """Test that main module can be imported."""
    assert main is not None
    assert hasattr(main, 'app')
    assert hasattr(main, 'healthcheck')


def test_aspect_ratio_endpoint():
    """Test the aspect ratio endpoint with mocked images of different aspect ratios."""
    # Create a mock image class
    class MockImage:
        def __init__(self, width, height):
            self.width = width
            self.height = height
    
    test_cases = [
        # (width, height, expected_ratio, description)
        (200, 100, 2.0, "landscape"),  # 2:1 landscape
        (100, 100, 1.0, "square"),     # 1:1 square  
        (100, 200, 0.5, "portrait")    # 1:2 portrait
    ]
    
    for width, height, expected_ratio, description in test_cases:
        mock_image = MockImage(width, height)
        
        with patch('main.get_image_from_url', new_callable=AsyncMock) as mock_get_image:
            mock_get_image.return_value = mock_image
            
            client = TestClient(main.app)
            response = client.get(f"/aspect-ratio/?query_url=http://example.com/{description}.jpg")
            
            assert response.status_code == 200
            data = response.json()
            assert "aspect_ratio" in data
            assert data["aspect_ratio"] == expected_ratio
            
            # Verify aspect ratio properties
            if description == "landscape":
                assert data["aspect_ratio"] > 1
            elif description == "square":
                assert data["aspect_ratio"] == 1
            elif description == "portrait":
                assert data["aspect_ratio"] < 1


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
