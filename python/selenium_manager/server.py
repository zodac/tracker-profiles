"""Entry point for launching the Flask application using the Waitress WSGI server.

This script:
- Imports the Flask app factory from the local package.
- Creates an app instance by calling `create_app()`.
- Starts the application using Waitress, bound to localhost on port 5000.

Usage:
    python -m <module_name>
"""

from waitress import serve

from . import create_app

if __name__ == "__main__":
    app = create_app()
    serve(app, host="127.0.0.1", port=5000)
