# SPDX-License-Identifier: 0BSD
# Copyright (c) 2024-2025 zodac.net

from collections.abc import Generator
from unittest.mock import MagicMock, patch

import pytest
from flask.testing import FlaskClient
from selenium_manager import create_app

HTTP_OK = 200


@pytest.fixture
def client() -> Generator:
    app = create_app()
    app.config["TESTING"] = True
    return app.test_client()


def test_ping(client: FlaskClient) -> None:
    res = client.get("/ping")
    assert res.status_code == HTTP_OK
    assert res.get_json() == {"status": "OK"}


@patch("selenium_manager.routes.uc.Chrome")
def test_open_and_close_session(mock_chrome: MagicMock, client: FlaskClient, tmp_path: "pytest.TempPathFactory") -> None:
    mock_driver = MagicMock()
    mock_driver.session_id = "test-session"
    mock_driver.service.service_url = "http://127.0.0.1:12345"
    mock_chrome.return_value = mock_driver

    data_dir = tmp_path / "data"
    data_dir.mkdir()

    res_open = client.post("/open", json={
        "browser_data_storage_path": str(data_dir),
        "browser_dimensions": "1024,768",
    })

    assert res_open.status_code == HTTP_OK
    res_json = res_open.get_json()
    assert "session_id" in res_json
    assert "session_url" in res_json

    session_id = res_json["session_id"]
    res_close = client.post("/close", json={"session_id": session_id})
    assert res_close.status_code == HTTP_OK
    assert res_close.get_json()["message"] == f"Session {session_id} closed"
