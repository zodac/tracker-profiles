from unittest.mock import MagicMock, patch

import pytest
from selenium_manager import create_app

@pytest.fixture
def client():
    app = create_app()
    app.config['TESTING'] = True
    yield app.test_client()

def test_ping(client):
    res = client.get("/ping")
    assert res.status_code == 200
    assert res.get_json() == {"status": "OK"}

@patch("selenium_manager.routes.uc.Chrome")
def test_open_and_close_session(mock_chrome, client, tmp_path):
    mock_driver = MagicMock()
    mock_driver.session_id = "test-session"
    mock_driver.service.service_url = "http://127.0.0.1:12345"
    mock_chrome.return_value = mock_driver

    data_dir = tmp_path / "data"
    data_dir.mkdir()

    res_open = client.post("/open", json={
        "browser_data_storage_path": str(data_dir),
        "browser_dimensions": "1024,768"
    })

    assert res_open.status_code == 200
    res_json = res_open.get_json()
    assert "session_id" in res_json
    assert "session_url" in res_json

    session_id = res_json["session_id"]
    res_close = client.post("/close", json={"session_id": session_id})
    assert res_close.status_code == 200
    assert res_close.get_json()["message"] == f"Session {session_id} closed"
