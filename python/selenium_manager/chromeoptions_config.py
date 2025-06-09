"""Configuration utilities for Chrome browser options.

This module defines helper functions to create and configure ChromeOptions
for use with undetected-chromedriver.
"""

import uuid
from pathlib import Path

import undetected_chromedriver as uc


def create_chrome_options(browser_data_storage_path: str, browser_dimensions: str) -> uc.ChromeOptions:
    """Create and configure ChromeOptions for launching an undetected Chrome browser.

    Args:
        browser_data_storage_path (str): Path to store user data and cache.
        browser_dimensions (str): Browser window size in the format 'WIDTH,HEIGHT'.

    Returns:
        uc.ChromeOptions: Configured Chrome options.
    """
    options = uc.ChromeOptions()

    browser_data_storage_path = Path(browser_data_storage_path)
    user_data_dir = browser_data_storage_path / f"session_{uuid.uuid4()}"
    disk_cache_dir = browser_data_storage_path / "selenium"

    options.add_argument(f"--window-size={browser_dimensions}")
    options.add_argument(f"--disk-cache-dir={disk_cache_dir}")
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
