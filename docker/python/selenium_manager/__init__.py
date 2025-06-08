"""Application factory for the Flask Selenium session manager.

This package:
- Configures structured and colored logging.
- Registers Flask routes for managing browser sessions.

The `create_app()` function serves as the public entry point for creating
and initializing a Flask application instance.
"""

from flask import Flask

from .logging_config import configure_logging
from .routes import register_routes


def create_app() -> Flask:
    """Create and configure the Flask application instance.

    This function:
    - Initializes the Flask app.
    - Sets up structured logging using `configure_logging()`.
    - Registers all route handlers with `register_routes()`.

    Returns:
        A fully configured Flask application instance.
    """
    app = Flask(__name__)
    configure_logging()
    register_routes(app)
    return app
