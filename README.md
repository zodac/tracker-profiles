# Tracker Profiles

## Overview

This is a tool used to log in to private torrent websites and take a screenshot of the user's profile page. This can be used to showcase stats on your
current tracker as part of an application to another site. It can also be used as a historical record of your stats on a tracker in case it goes down
or becomes otherwise unavailable.

## Features

- Opens the selected trackers and logs in, navigating to the user's profile page
- Redacts the user's email address and IP address (replacing the text with "----")
- Takes a full-page screenshot of the redacted user profile

## Trackers

### Supported Trackers

The available trackers come in three types:

- Headless: Can run with the browser in headless mode, meaning no UI browser is needed
- Manual: There is some user interaction needed (a Captcha or 2FA to log in, etc.), requiring a UI browser
- Non-English: If the tracker is not in English, a UI browser is required to translate the page

| Tracker Name        | Support         |
|---------------------|-----------------|
| ABTorrents          | Headless        |
| Aither              | Headless        |
| AlphaRatio          | Headless        |
| AnimeBytes          | Headless        |
| Anthelion           | Headless        |
| BackUps             | Headless        |
| BakaBT              | Headless        |
| BeyondHD            | **Manual**      |
| Blutopia            | Headless        |
| BwTorrents          | Headless        |
| Cathode-Ray.Tube    | Headless        |
| CGPeers             | **Manual**      |
| DICMusic            | Headless        |
| DigitalCore.Club    | **Manual**      |
| DocsPedia           | **Manual**      |
| Empornium           | Headless        |
| FearNoPeer          | Headless        |
| FileList            | Headless        |
| GazelleGames        | **Manual**      |
| HDBits              | **Manual**      |
| HDUnited            | Headless        |
| Kufirc              | **Non-English** |
| Lat-Team            | **Non-English** |
| Libble              | Headless        |
| LST                 | Headless        |
| Metal-Tracker       | Headless        |
| MoreThanTV          | Headless        |
| MyAnonaMouse        | Headless        |
| Nebulance           | Headless        |
| Orpheus             | Headless        |
| PassThePopcorn      | **Manual**      |
| PixelCove           | Headless        |
| PornBay             | Headless        |
| PrivateSilverScreen | Headless        |
| Redacted            | Headless        |
| ReelFlix            | Headless        |
| RUTracker           | **Non-English** |
| SecretCinema        | Headless        |
| SeedPool            | Headless        |
| Tasmanites          | Headless        |
| TeamOS              | Headless        |
| TheEmpire           | **Manual**      |
| TheGeeks            | **Manual**      |
| TorrentLeech        | Headless        |
| TVChaosUK           | Headless        |
| UHDBits             | Headless        |
| Unwalled            | Headless        |

### Unsupported Trackers

The following trackers are not currently supported:

| Tracker Name    | Reason                  |
|-----------------|-------------------------|
| AnimeTorrents   | Cloudflare verification |
| AvistaZ         | Cloudflare verification |
| BroadcasThe.Net | Cloudflare verification |
| CinemaZ         | Cloudflare verification |
| ExoticaZ        | Cloudflare verification |
| Hawke-Uno       | Cloudflare verification |
| IPTorrents      | Cloudflare verification |
| PrivateHD       | Cloudflare verification |
| SceneTime       | Cloudflare verification |
| Speed.CD        | Cloudflare verification |
| SportsCult      | Cloudflare verification |
| ULCX            | Cloudflare verification |

## How To Use

### Tracker Defintions

First, copy the [trackers_example.csv](./docker/trackers_example.csv) file and rename it to **trackers.csv**.
This file needs to be updated with your user's login information for each tracker. Any unwanted trackers can be deleted, or prefixed by the
`CSV_COMMENT_SYMBOL` environment variable so they are excluded. The file can be saved anywhere, and it will be referenced when running
the application.

### Running Application

The application is run using Docker. There are two ways to execute the application - with a UI and without. By default, the application is configured
to only screenshot trackers that do not require a UI. A UI is needed for trackers that require some user input during login (like a Captcha or 2FA),
or for a non-English tracker that needs to be translated.

To run through Docker with a UI, local connections to the host display must be enabled. Please note this will be reset upon reboot and may need to be
reapplied:

```bash
xhost +local:
```

Below is the command to run the `latest` docker image. `--env DISPLAY="${DISPLAY}"` and `-v /tmp/.X11-unix:/tmp/.X11-unix` are only required if a UI
is needed, for either of the following configurations:

- `ENABLE_MANUAL_TRACKERS` is **true** (setting this to **false** will override setting `ENABLE_TRANSLATION_TO_ENGLISH` to **true**)
- `ENABLE_HEADLESS_BROWSER` is **false**.

```bash
docker run \
    --env DISPLAY="${DISPLAY}" \
    --env BROWSER_DATA_STORAGE_PATH=/tmp/chrome \
    --env BROWSER_HEIGHT=1050 \
    --env BROWSER_WIDTH=1680 \
    --env CSV_COMMENT_SYMBOL='#' \
    --env ENABLE_HEADLESS_BROWSER=true \
    --env ENABLE_MANUAL_TRACKERS=false \
    --env ENABLE_TRANSLATION_TO_ENGLISH=false \
    --env LOG_LEVEL=INFO \
    --env OPEN_OUTPUT_DIRECTORY=false \
    --env OUTPUT_DIRECTORY_NAME_FORMAT=yyyy-MM-dd \
    --env OUTPUT_DIRECTORY_PARENT_PATH=/tmp/screenshots \
    --env TIMEZONE=UTC \
    --env TRACKER_INPUT_FILE_PATH=/tmp/screenshots/trackers.csv \
    -v /tmp/.X11-unix:/tmp/.X11-unix \
    -v /tmp/chrome:/tmp/chrome \
    -v /tmp/screenshots:/tmp/screenshots \
    --rm zodac/tracker-profiles:latest
```

### Configuration Options

The following are all possible configuration options, defined as environment variables for the docker image:

| Environment Variable            | Description                                                                                                         | Default Value                 |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------|-------------------------------|
| *BROWSER_DATA_STORAGE_PATH*     | The file path in which to store browser data (profiles, caches, etc.)                                               | /tmp/chrome                   |
| *BROWSER_HEIGHT*                | The height (in pixels) of the web browser used to take screenshots                                                  | 1050                          |
| *BROWSER_WIDTH*                 | The width (in pixels) of the web browser used to take screenshots                                                   | 1680                          |
| *CSV_COMMENT_SYMBOL*            | If this character is the first in a CSV row, the CSV row is considered a comment and not processed                  | #                             |
| *ENABLE_HEADLESS_BROWSER*       | Enables a headless browser for screenshots (otherwise uses a browser with UI)                                       | true                          |
| *ENABLE_MANUAL_TRACKERS*        | Whether to take screnshots of trackers that require a browser with a UI (overrides `ENABLE_TRANSLATION_TO_ENGLISH`) | false                         |
| *ENABLE_TRANSLATION_TO_ENGLISH* | Whether to translate non-English trackers to English (only if the tracker has no English option)                    | false                         |
| *LOG_LEVEL*                     | The logging level for console output                                                                                | INFO                          |
| *OPEN_OUTPUT_DIRECTORY*         | Whether to open the output directory when execution is complete (not supported in Docker, debug only)               | false                         |
| *OUTPUT_DIRECTORY_NAME_FORMAT*  | The name of the output directory to be created for the of the screenshots                                           | yyyy-MM-dd                    |
| *OUTPUT_DIRECTORY_PARENT_PATH*  | The output location of for the new directory created for the screenshots, relative to the project root              | /tmp/screenshots              |
| *TIMEZONE*                      | The local timezone, used to retrieve the current date to name the output directory                                  | UTC                           |
| *TRACKER_INPUT_FILE_PATH*       | The path to the input tracker definition CSV file (inside the docker container)                                     | /tmp/screenshots/trackers.csv |

## Contributing

### Requirements

- [Apache Maven (v3.9.9)](https://maven.apache.org/download.cgi)
- [Google Chrome](https://www.google.com/chrome/) (only if not using Docker)
- [Java (JDK 24)](https://jdk.java.net/24/)

### Debugging Application

Using IntelliJ, and click on **Run**> **Edit Configurations** and add the environment variables for the application. Once done, open
the [ProfileScreenshotter.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/ProfileScreenshotter.java) and run the `main`
method from the IDE.
The [AbstractTrackerHandler.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler/AbstractTrackerHandler.java) implementation
for each tracker is retrieved by the *trackerName* field within the CSV file.

[Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/) is used to leverage the Chromium web browser to take screenshots. While the
application usually run in headless mode, this can be changed by updating the `ENABLE_HEADLESS_BROWSER` value in
the [configuration](#configuration-options). This will cause a new browser instance to launch when taking a screenshot, and can be used for debugging
a new implementation.

### Building and Running In Docker

Below is the command to build and run the development docker image with everything enabled (requires the UI to be defined):

```bash
docker build -f ./docker/Dockerfile -t tracker-profiles . &&
docker run \
    --env DISPLAY="${DISPLAY}" \
    --env BROWSER_DATA_STORAGE_PATH=/tmp/chrome \
    --env BROWSER_HEIGHT=1050 \
    --env BROWSER_WIDTH=1680 \
    --env CSV_COMMENT_SYMBOL='#' \
    --env ENABLE_HEADLESS_BROWSER=true \
    --env ENABLE_MANUAL_TRACKERS=true \
    --env ENABLE_TRANSLATION_TO_ENGLISH=true \
    --env LOG_LEVEL=INFO \
    --env OPEN_OUTPUT_DIRECTORY=false \
    --env OUTPUT_DIRECTORY_NAME_FORMAT=yyyy-MM-dd \
    --env OUTPUT_DIRECTORY_PARENT_PATH=/tmp/screenshots \
    --env TIMEZONE=UTC \
    --env TRACKER_INPUT_FILE_PATH=/tmp/screenshots/trackers.csv \
    -v /tmp/.X11-unix:/tmp/.X11-unix \
    -v /tmp/chrome:/tmp/chrome \
    -v /tmp/screenshots:/tmp/screenshots \
    --rm tracker-profiles
```

### Implementing New Tracker Handlers

All supported private trackers have an implementation found in the [handler](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler)
package. To add a new one,
extend [AbstractTrackerHandler.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler/AbstractTrackerHandler.java), following
the convention from an existing implementation
like [AitherHandler.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler/AbTorrentsHandler.java).
