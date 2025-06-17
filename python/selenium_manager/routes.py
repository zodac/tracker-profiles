# SPDX-License-Identifier: 0BSD
# Copyright (c) 2024-2025 zodac.net

"""Flask route definitions for managing Selenium browser sessions using undetected-chromedriver.

Routes:
- `GET /ping`: Health check.
- `POST /open`: Launch a new browser session with specified options.
- `POST /close`: Terminate an existing session by ID.

Environment:
- Expects paths and settings in the incoming JSON requests.
- Validates access to filesystem and browser resolution format.

Used internally with a Flask app via `register_routes()`.
"""

import logging
import os
from pathlib import Path
from threading import Lock
from typing import TypedDict

import undetected_chromedriver as uc
from flask import Flask, Response, jsonify, request
from selenium.webdriver.remote.webdriver import WebDriver

from .chromeoptions_config import create_chrome_options

sessions: dict[str, WebDriver] = {}
lock = Lock()
logger = logging.getLogger(__name__)


class OpenRequestData(TypedDict):
    """Represents the expected JSON payload for the /open endpoint.

    Attributes:
        browser_data_storage_path (str): Filesystem path where browser session data will be stored.
        browser_dimensions (str): Comma-separated dimensions for the browser window, e.g., "1920,1080".
    """
    browser_data_storage_path: str
    browser_dimensions: str


class CloseRequestData(TypedDict):
    """Represents the expected JSON payload for the /close endpoint.

    Attributes:
        session_id (str): Identifier of the browser session to close.
    """
    session_id: str


def register_routes(app: Flask) -> None:
    """Register all Flask routes for managing browser sessions.

    Args:
        app: The Flask application to which the routes will be attached.
    """

    @app.route("/ping", methods=["GET"])
    def ping() -> Response:
        """Health check endpoint.

        Returns:
            200 OK response with an empty body to indicate the service is running.
        """
        return jsonify({"status": "OK"}), 200

    @app.route("/open", methods=["POST"])
    def open_browser_session() -> Response:
        """Start a new undetected Chrome browser session with the provided configuration.

        Expects JSON payload with:
            'browser_data_storage_path': str, writable path for browser profile and cache.
            'browser_dimensions': str, format 'WIDTH,HEIGHT' for window size.

        Returns:
            200 OK with session ID and local session URL on success.
            400 Bad Request for missing/invalid input.
            500 Internal Server Error for unexpected issues during browser startup.
        """
        logger.info("\t- /open request received")
        data: dict[str, str] | None = request.get_json()

        # Validation of input data
        logger.debug("\t- Request payload: %s", data)
        if not data:
            return jsonify({"error": "Missing JSON body"}), 400
        try:
            request_data: OpenRequestData = {
                "browser_data_storage_path": data["browser_data_storage_path"],
                "browser_dimensions": data["browser_dimensions"],
            }
        except KeyError as e:
            return jsonify({"error": f"Missing required key: {e.args[0]}"}), 400

        browser_data_storage_path = request_data["browser_data_storage_path"]
        # Check if path exists
        if not Path(browser_data_storage_path).exists():
            return jsonify({"error": f"'browser_data_storage_path' '{browser_data_storage_path}' does not exist"}), 400
        # Check write permission
        if not os.access(browser_data_storage_path, os.W_OK):
            return jsonify({"error": f"No write permission for 'browser_data_storage_path' '{browser_data_storage_path}'"}), 400

        browser_dimensions = request_data["browser_dimensions"]
        # Validate browser_dimensions format
        if not isinstance(browser_dimensions, str) or "," not in browser_dimensions:
            return jsonify({"error": f"Invalid 'browser_dimensions' format, expected 'WIDTH,HEIGHT', found: '{browser_dimensions}'"}), 400

        try:
            options = create_chrome_options(browser_data_storage_path, browser_dimensions)
            driver = uc.Chrome(headless=False, use_subprocess=False, options=options)

            session_id = driver.session_id
            port = driver.service.service_url.split(":")[-1]

            logger.info("\t\t- Started session '%s' on port %s", session_id, port)

            with lock:
                sessions[session_id] = driver

            return jsonify({
                "session_id": session_id,
                "session_url": f"http://127.0.0.1:{port}",
            }), 200
        except Exception as e:
            logger.exception("\t- Failed to create browser session")
            return jsonify({"error": str(e)}), 500

    @app.route("/close", methods=["POST"])
    def close_browser_session() -> Response:
        """Close an existing browser session.

        Expects JSON payload with:
            'session_id': str, the ID of the session to close.

        Returns:
            200 OK with a confirmation message on success.
            400 Bad Request if session_id is missing.
            404 Not Found if session ID does not exist.
            500 Internal Server Error if an error occurs while closing the session.
        """
        logger.info("\t- /close request received")
        data: dict[str, str] | None = request.get_json()

        # Validation of input data
        logger.debug("\t- Request payload: %s", data)
        try:
            request_data: CloseRequestData = {
                "session_id": data["session_id"],
            }
        except (TypeError, KeyError):
            logger.exception("Missing or invalid 'session_id' field")
            return jsonify({"error": "Missing session_id"}), 400

        session_id = request_data["session_id"]

        with lock:
            driver = sessions.pop(session_id, None)

        if not driver:
            logger.warning("\t- No session found for ID %s", session_id)
            return jsonify({"error": "Session not found"}), 404

        try:
            driver.quit()
            logger.info("\t\t- Session '%s' closed", session_id)
            return jsonify({"message": f"Session {session_id} closed"}), 200
        except Exception as e:
            logger.exception("\t- Failed to close session %s", session_id)
            return jsonify({"error": str(e)}), 500
