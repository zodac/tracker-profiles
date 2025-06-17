# SPDX-License-Identifier: 0BSD
# Copyright (c) 2024-2025 zodac.net

from pathlib import Path
from typing import Any

from selenium.webdriver.chrome.options import Options
from selenium_manager.chromeoptions_config import create_chrome_options


def test_chrome_options_arguments(tmp_path: Path) -> None:
    options: Options = create_chrome_options(str(tmp_path), "1200,800")

    args: list[str] = options.arguments
    assert "--window-size=1200,800" in args

    # Check that user-data-dir points inside tmp_path
    user_data_arg: str | None = next((arg for arg in args if "--user-data-dir=" in arg), None)
    assert user_data_arg is not None
    assert Path(user_data_arg.split("=", 1)[1]).parent == tmp_path

    # Check disk-cache-dir is under the path
    cache_arg: str | None = next((arg for arg in args if "--disk-cache-dir=" in arg), None)
    assert cache_arg is not None
    assert Path(cache_arg.split("=", 1)[1]) == tmp_path / "selenium"


def test_chrome_options_prefs(tmp_path: Path) -> None:
    options: Options = create_chrome_options(str(tmp_path), "800,600")
    prefs: dict[str, Any] = options.experimental_options["prefs"]
    assert prefs["credentials_enable_service"] is False
    assert prefs["profile.password_manager_enabled"] is False
