import logging
import os
import uuid
from datetime import datetime, timezone
from logging import LogRecord
from threading import Lock
from typing import TypedDict
from zoneinfo import ZoneInfo

import undetected_chromedriver as uc
from colorlog import ColoredFormatter
from flask import Flask, Response, jsonify, request
from selenium.webdriver.remote.webdriver import WebDriver
from waitress import serve

app = Flask(__name__)

# Thread-safe session store
sessions: dict[str, WebDriver] = {}
lock = Lock()

class OpenRequestData(TypedDict):
    """
    Represents the expected JSON payload for the /open endpoint.

    Attributes:
        browser_data_storage_path (str): Filesystem path where browser session data will be stored.
        browser_dimensions (str): Comma-separated dimensions for the browser window, e.g., "1920,1080".
    """
    browser_data_storage_path: str
    browser_dimensions: str

class CloseRequestData(TypedDict):
    """
    Represents the expected JSON payload for the /close endpoint.

    Attributes:
        session_id (str): Identifier of the browser session to close.
    """
    session_id: str

@app.route("/ping", methods=["GET"])
def ping() -> Response:
    """
    Health check endpoint.

    Returns:
        - 200 OK response with an empty body to indicate the service is running.
    """
    return jsonify({"status": "OK"}), 200

@app.route("/open", methods=["POST"])
def open_browser_session() -> Response:
    """
    Starts a new undetected Chrome browser session with the provided configuration.

    Expects JSON payload with:
        - 'browser_data_storage_path': str, writable path for browser profile and cache.
        - 'browser_dimensions': str, format 'WIDTH,HEIGHT' for window size.

    Returns:
        - 200 OK with session ID and local session URL on success.
        - 400 Bad Request for missing/invalid input.
        - 500 Internal Server Error for unexpected issues during browser startup.
    """
    logging.info("\t- /open request received")
    data: dict[str, str] | None = request.get_json()

    # Validation of input data
    logging.debug(f"\t- Request payload: {data}")
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
    if not os.path.exists(browser_data_storage_path):
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

        logging.info(f"\t\t- Started session '{session_id}' on port {port}")

        with lock:
            sessions[session_id] = driver

        return jsonify({
            "session_id": session_id,
            "session_url": f"http://127.0.0.1:{port}",
        }), 200

    except Exception as e:
        logging.exception("\t- Failed to create browser session")
        return jsonify({"error": str(e)}), 500


@app.route("/close", methods=["POST"])
def close_browser_session() -> Response:
    """
    Closes an existing browser session.

    Expects JSON payload with:
        - 'session_id': str, the ID of the session to close.

    Returns:
        - 200 OK with a confirmation message on success.
        - 400 Bad Request if session_id is missing.
        - 404 Not Found if session ID does not exist.
        - 500 Internal Server Error if an error occurs while closing the session.
    """
    logging.info("\t- /close request received")
    data: dict[str, str] | None = request.get_json()

    # Validation of input data
    logging.debug(f"\t- Request payload: {data}")
    try:
        request_data: CloseRequestData = {
            "session_id": data["session_id"],
        }
    except (TypeError, KeyError):
        logging.error("Missing or invalid 'session_id' field")
        return jsonify({"error": "Missing session_id"}), 400

    session_id = request_data["session_id"]

    with lock:
        driver = sessions.pop(session_id, None)

    if not driver:
        logging.warning(f"\t- No session found for ID {session_id}")
        return jsonify({"error": "Session not found"}), 404

    try:
        driver.quit()
        logging.info(f"\t\t- Session '{session_id}' closed")
        return jsonify({"message": f"Session {session_id} closed"}), 200
    except Exception as e:
        logging.exception(f"\t- Failed to close session {session_id}")
        return jsonify({"error": str(e)}), 500

def create_chrome_options(browser_data_storage_path: str, browser_dimensions: str) -> uc.ChromeOptions:
    """
    Creates and configures ChromeOptions for launching an undetected Chrome browser.

    Args:
        - browser_data_storage_path (str): Path to store user data and cache.
        - browser_dimensions (str): Browser window size in the format 'WIDTH,HEIGHT'.

    Returns:
        - uc.ChromeOptions: Configured Chrome options.
    """
    options = uc.ChromeOptions()

    options.add_argument(f"--window-size={browser_dimensions}")
    options.add_argument(f"--disk-cache-dir={os.path.join(browser_data_storage_path, "selenium")}")

    user_data_dir = os.path.join(browser_data_storage_path, f"session_{uuid.uuid4()}")
    options.add_argument(f"--user-data-dir={user_data_dir}")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--disable-notifications")
    options.add_argument("--disable-blink-features=AutomationControlled")

    prefs = {
        "credentials_enable_service": False,
        "profile.password_manager_enabled": False,
    }
    options.add_experimental_option("prefs", prefs)

    return options

def configure_logging() -> None:
    """
    Configures the root logger with colored output and support for a custom TRACE level.

    The log level is determined by the LOG_LEVEL environment variable (defaults to INFO).
    Logs are color-coded using `colorlog` and formatted to show milliseconds.
    """
    trace_level_index = 5
    # Add support for TRACE level logging
    logging.addLevelName(trace_level_index, "TRACE")

    def trace(self, message, *args, **kwargs):
        if self.isEnabledFor(trace_level_index):
            self._log(trace_level_index, message, args, **kwargs)

    logging.Logger.trace = trace
    date_format="%Y-%m-%d %H:%M:%S.%f"

    class CustomFormatter(ColoredFormatter):
        def formatTime(self, record: LogRecord, datefmt: str | None = None) -> str:
            # Use the TIMEZONE environment variable if set, otherwise default to UTC
            tz_name = os.getenv("TIMEZONE")
            local_tz = ZoneInfo(tz_name) if tz_name else timezone.UTC

            t = datetime.fromtimestamp(record.created, tz=local_tz)
            s = t.strftime(datefmt or date_format)
            return s[:-3]  # Trim microseconds to milliseconds

    log_format = (
        "%(asctime)s "
        "[%(log_color)s%(levelname)-4s%(reset)s] "
        "%(message)s"
    )

    log_colours = {
        "TRACE":    "bold_purple",
        "DEBUG":    "bold_green",
        "INFO":     "bold_blue",
        "WARNING":  "bold_yellow",
        "ERROR":    "bold_red",
        "CRITICAL": "bold_red",
    }

    formatter = CustomFormatter(
        log_format,
        datefmt=date_format,
        log_colors=log_colours,
    )

    handler = logging.StreamHandler()
    handler.setFormatter(formatter)

    root_logger = logging.getLogger()
    root_logger.setLevel(os.getenv("LOG_LEVEL", "INFO").upper())
    root_logger.handlers = [handler]

    logging.getLogger(__name__).trace("Logging is configured")

if __name__ == "__main__":
    """
    Entry point for running the Flask app with Waitress.

    Starts the server on 0.0.0.0:5000 and configures logging beforehand.
    """
    configure_logging()
    logging.info("Starting Waitress server to handle Python Selenium sessions")
    serve(app, host="0.0.0.0", port=5000)
