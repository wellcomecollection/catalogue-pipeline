import numpy as np
import base64
from unittest.mock import patch, AsyncMock
import main
from fastapi.testclient import TestClient


def test_import_main_works():
    """Test that main module can be imported."""
    assert main is not None
    assert hasattr(main, 'app')
    assert hasattr(main, 'healthcheck')


def test_palette_endpoint():
    """Test the palette endpoint with mocked dependencies."""
    # Create a mock image object
    class MockImage:
        def __init__(self):
            self.width = 100
            self.height = 100
    
    mock_image = MockImage()
    
    # Mock palette result from PaletteEncoder
    mock_palette_result = {
        "palette_embedding": np.array([0.1, 0.2, 0.3, 0.4], dtype=np.float32),
        "average_color_hex": "#FF0080"
    }
    
    with patch('main.get_image_from_url', new_callable=AsyncMock) as mock_get_image, \
         patch('main.batch_inferrer_queue.execute', new_callable=AsyncMock) as mock_execute:
        
        mock_get_image.return_value = mock_image
        mock_execute.return_value = mock_palette_result
        
        client = TestClient(main.app)
        response = client.get("/palette/?query_url=http://example.com/image.jpg")
        
        assert response.status_code == 200
        data = response.json()
        assert "palette_embedding" in data
        assert "average_color_hex" in data
        assert data["average_color_hex"] == "#FF0080"
        
        # Verify base64 encoding
        decoded_embedding = np.frombuffer(base64.b64decode(data["palette_embedding"]), dtype=np.float32)
        assert np.allclose(decoded_embedding, mock_palette_result["palette_embedding"])


def test_palette_endpoint_error_handling():
    """Test the palette endpoint error handling when image URL is invalid."""
    with patch('main.get_image_from_url', new_callable=AsyncMock) as mock_get_image:
        # Simulate ValueError from get_image_from_url
        mock_get_image.side_effect = ValueError("Invalid image URL")
        
        client = TestClient(main.app)
        response = client.get("/palette/?query_url=http://invalid-url.com/bad.jpg")
        
        assert response.status_code == 404
        data = response.json()
        assert "detail" in data


def test_palette_response_structure():
    """Test the expected structure of palette API response."""
    # Test base64 encoding capability for palette embeddings
    import base64
    
    # Create a sample embedding like what PaletteEncoder would return
    embedding = np.array([0.1, 0.2, 0.3, 0.4], dtype=np.float32)
    average_color_hex = "#123456"
    
    # Test the response structure matches what main.py returns
    mock_response = {
        "palette_embedding": base64.b64encode(embedding),
        "average_color_hex": average_color_hex,
    }
    
    assert "palette_embedding" in mock_response
    assert "average_color_hex" in mock_response
    assert isinstance(mock_response["palette_embedding"], bytes)
    assert mock_response["average_color_hex"].startswith("#")
    
    # Test that it can be decoded back
    decoded = np.frombuffer(base64.b64decode(mock_response["palette_embedding"]), dtype=np.float32)
    assert np.allclose(embedding, decoded)



def test_healthcheck_function_exists():
    """Test that the healthcheck function exists."""
    assert hasattr(main, "healthcheck")
    response = main.healthcheck()
    assert response == {"status": "healthy"}
