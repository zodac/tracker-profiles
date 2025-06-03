import os
import time
import uuid
import logging
import undetected_chromedriver as uc
from colorlog import ColoredFormatter
from flask import Flask, request, jsonify
from threading import Lock
from waitress import serve

app = Flask(__name__)

# Thread-safe session store
sessions = {}
lock = Lock()

@app.route('/ping', methods=['GET'])
def ping():
    return '', 200

@app.route('/open', methods=['POST'])
def open_browser():
    logging.info("\t- /open request received")
    data = request.get_json()

    logging.debug(f"\t- Request payload: {data}")
    if not data:
        return jsonify({'error': 'Missing JSON body'}), 400

    required_keys = ['browser_data_storage_path', 'browser_dimensions']
    missing_keys = [key for key in required_keys if key not in data]
    if missing_keys:
        return jsonify({'error': f'Missing required keys: {", ".join(missing_keys)}'}), 400

    browser_data_storage_path = data['browser_data_storage_path']
    # Check if path exists
    if not os.path.exists(browser_data_storage_path):
        return jsonify({'error': f"'browser_data_storage_path' '{browser_data_storage_path}' does not exist"}), 400

    # Check write permission
    if not os.access(browser_data_storage_path, os.W_OK):
        return jsonify({'error': f"No write permission for 'browser_data_storage_path' '{browser_data_storage_path}'"}), 400

    browser_dimensions = data['browser_dimensions']
    # Validate browser_dimensions format
    if not isinstance(browser_dimensions, str) or ',' not in browser_dimensions:
        return jsonify({'error': f"Invalid 'browser_dimensions' format, expected 'WIDTH,HEIGHT', found: '{browser_dimensions}'"}), 400

    try:
        options = create_chrome_options(browser_data_storage_path, browser_dimensions)
        driver = uc.Chrome(headless=False, use_subprocess=False, options=options)

        session_id = driver.session_id
        port = driver.service.service_url.split(":")[-1]

        logging.info(f"\t\t- Started session '{session_id}' on port {port}")

        with lock:
            sessions[session_id] = driver

        return jsonify({
            'session_id': session_id,
            'session_url': f"http://127.0.0.1:{port}"
        }), 200

    except Exception as e:
        logging.exception("\t- Failed to create browser session")
        return jsonify({'error': str(e)}), 500


@app.route('/close', methods=['POST'])
def close_session():
    logging.info("\t- /close request received")
    data = request.get_json()
    logging.debug(f"\t- Request payload: {data}")

    session_id = data.get('session_id')
    if not session_id:
        logging.error("Missing 'session_id' field")
        return jsonify({'error': 'Missing session_id'}), 400

    with lock:
        driver = sessions.pop(session_id, None)

    if not driver:
        logging.warning(f"\t- No session found for ID {session_id}")
        return jsonify({'error': 'Session not found'}), 404

    try:
        driver.quit()
        logging.info(f"\t\t- Session '{session_id}' closed")
        return jsonify({'message': f'Session {session_id} closed'}), 200
    except Exception as e:
        logging.exception(f"\t- Failed to close session {session_id}")
        return jsonify({'error': str(e)}), 500

def create_chrome_options(browser_data_storage_path: str, browser_dimensions: str) -> uc.ChromeOptions:
    """
    Configure and return ChromeOptions for the browser session.
    """
    options = uc.ChromeOptions()

    options.add_argument(f"--window-size={browser_dimensions}")
    options.add_argument(f"--disk-cache-dir={os.path.join(browser_data_storage_path, 'selenium')}")

    user_data_dir = os.path.join(browser_data_storage_path, f"session_{uuid.uuid4()}")
    options.add_argument(f"--user-data-dir={user_data_dir}")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--disable-notifications")
    options.add_argument("--disable-blink-features=AutomationControlled")

    prefs = {
        "credentials_enable_service": False,
        "profile.password_manager_enabled": False
    }
    options.add_experimental_option("prefs", prefs)

    return options

def configure_logging():
    # Add support for TRACE level logging
    TRACE_LEVEL_NUM = 5
    logging.addLevelName(TRACE_LEVEL_NUM, "TRACE")

    def trace(self, message, *args, **kws):
        if self.isEnabledFor(TRACE_LEVEL_NUM):
            self._log(TRACE_LEVEL_NUM, message, args, **kws)

    logging.Logger.trace = trace

    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()

    LOG_FORMAT = (
        "%(asctime)s "
        "[%(log_color)s%(levelname)-4s%(reset)s] "
        "%(message)s"
    )

    DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"

    LOG_COLORS = {
        'TRACE':    'bold_purple',
        'DEBUG':    'bold_green',
        'INFO':     'bold_blue',
        'WARNING':  'bold_yellow',
        'ERROR':    'bold_red',
        'CRITICAL': 'bold_red',
    }

    class CustomFormatter(ColoredFormatter):
        def formatTime(self, record, datefmt=None):
            from datetime import datetime
            t = datetime.fromtimestamp(record.created)
            s = t.strftime(datefmt or DATE_FORMAT)
            return s[:-3]  # Trim microseconds to milliseconds

    formatter = CustomFormatter(
        LOG_FORMAT,
        datefmt=DATE_FORMAT,
        log_colors=LOG_COLORS
    )

    handler = logging.StreamHandler()
    handler.setFormatter(formatter)

    root_logger = logging.getLogger()
    root_logger.setLevel(LOG_LEVEL)
    root_logger.handlers = [handler]

    logging.getLogger(__name__).trace("Logging is configured")

if __name__ == '__main__':
    configure_logging()
    logging.info(f"Starting Waitress server to handle Python Selenium sessions")
    serve(app, host="0.0.0.0", port=5000)
