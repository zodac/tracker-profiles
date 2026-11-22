# SPDX-License-Identifier: 0BSD
# Copyright (c) 2024-2026 zodac.net
"""Writes a backup of demo data, restored into an empty application before the screenshots of the web UI in the README are taken.

The backup (see BackupManifest) holds made-up credentials for a few trackers (some enabled, some disabled), the result of their latest
execution (one failed), and enough successful durations for each to have an estimated duration. It has no screenshot files.

This is not part of the Docker image: it is only run by .github/scripts/generate_readme_screenshots.sh.

Usage: python3 readme_demo_backup.py <output-zip>
"""

import argparse
import csv
import io
import json
import time
import zipfile

# Tracker name -> (enabled, successful), for a mix of headless and manual trackers
TRACKERS = {
    "Aither": (True, True),
    "AnimeBytes": (True, True),
    "BeyondHD": (True, True),
    "Blutopia": (True, True),
    "BroadcasThe.Net": (True, True),
    "FileList": (True, False),
    "HDBits": (True, True),
    "IPTorrents": (True, True),
    "Orpheus": (True, True),
    "PassThePopcorn": (True, True),
    "Redacted": (True, True),
    "TorrentLeech": (True, True),
    "SceneTime": (False, True),
    "Speed.CD": (False, True),
}

DURATIONS_PER_TRACKER = 5
MILLIS_PER_SECOND = 1_000
MILLIS_PER_HOUR = 3_600_000
MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR


def main() -> None:
    """Write the demo backup to the path given as the only argument."""
    parser = argparse.ArgumentParser(description="Writes a backup of demo data for the README screenshots")
    parser.add_argument("output", help="the path of the backup (a zip file) to write")
    output = parser.parse_args().output

    now = int(time.time() * MILLIS_PER_SECOND)
    credentials = io.StringIO()
    writer = csv.writer(credentials, lineterminator="\n")
    writer.writerow(["trackerName", "username", "password"])

    results = []
    durations = []
    for index, (name, (_, successful)) in enumerate(TRACKERS.items()):
        writer.writerow([name, "demo-user", "demo-password"])
        finished_at = now - MILLIS_PER_HOUR - index * 45 * MILLIS_PER_SECOND
        results.append({"name": name, "finishedAt": finished_at, "successful": successful})

        # A different, stable duration for each tracker (between 25s and 95s), from its latest executions (one a day)
        base_duration = (25 + (index * 37) % 70) * MILLIS_PER_SECOND
        durations.extend(
            {
                "name": name,
                "finishedAt": finished_at - run * MILLIS_PER_DAY,
                "duration": base_duration + (run * 1_700) % 6_000,
            }
            for run in range(DURATIONS_PER_TRACKER)
        )

    manifest = {
        "format": "tracker-profiles-backup",
        "formatVersion": 1,
        "applicationVersion": "demo",
        "createdAt": now,
        "credentialsIncluded": True,
        "trackers": [{"name": name, "enabled": enabled} for name, (enabled, _) in TRACKERS.items()],
        "screenshotsIncluded": False,
        "screenshotFiles": {},
        "database": {"settings": {}, "results": results, "screenshots": [], "durations": durations},
    }

    # The manifest must be the first entry, followed by the credentials
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("backup.json", json.dumps(manifest, indent=2))
        archive.writestr("trackers.csv", credentials.getvalue())


if __name__ == "__main__":
    main()
