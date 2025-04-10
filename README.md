# Tracker Profiles

## Overview

This is a tool used to log in to private torrent websites and take a screenshot of the user's profile page.

## Tracker Defintions

First, copy the [trackers_example.csv](./docker/trackers_example.csv) file and rename it to **trackers.csv**.
This file needs to be updated with your user's information for each tracker. The
[AbstractTrackerHandler.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler/AbstractTrackerHandler.java) implementation for
each tracker is retrieved by the *trackerName* field within the CSV file. The file can be saved
anywhere, and it will be referenced when running the application.

## Running Application

The application is run using Docker. There are two ways to execute the application - with a UI and without. A UI is needed for trackers that require
some user input during login (like a Captcha or 2FA), or for a non-English tracker that needs to be translated. Running without a UI is possible, but
those specific trackers will be excluded.

To run through Docker with a UI, local connections to the host display must be enabled. Please note this will be reset upon reboot and may need to be
reapplied:

```bash
xhost +local:
```

Then build and run the docker image:

```bash
docker build -f ./docker/Dockerfile -t profile-screenshotter .
docker run \
    --env "DISPLAY=${DISPLAY}" \
    --env BROWSER_DATA_STORAGE_PATH=/tmp/chrome \
    --env BROWSER_HEIGHT=1050 \
    --env BROWSER_WIDTH=1680 \
    --env CSV_COMMENT_SYMBOL='#' \
    --env EMAIL_ADDRESSES= \
    --env INCLUDE_MANUAL_TRACKERS=false \
    --env IP_ADDRESSES= \
    --env LOG_LEVEL=INFO \
    --env OPEN_OUTPUT_DIRECTORY=false \
    --env OUTPUT_DIRECTORY_NAME_FORMAT=yyyy-MM-dd \
    --env OUTPUT_DIRECTORY_PARENT_PATH=/tmp/screenshots \
    --env TIMEZONE=UTC \
    --env TRACKER_INPUT_FILE_PATH=/tmp/screenshots/trackers.csv \
    --env USE_HEADLESS_BROWSER=true \
    -v /tmp/.X11-unix:/tmp/.X11-unix \
    -v /tmp/screenshots:/tmp/screenshots \
    -v /tmp/chrome:/tmp/chrome \
    --rm profile-screenshotter
```

## Configuration Options

The following are all possible configuration options, defined as environment variables for the docker image:

| Environment Variable           | Description                                                                                                    | Default Value                 |
|--------------------------------|----------------------------------------------------------------------------------------------------------------|-------------------------------|
| *BROWSER_DATA_STORAGE_PATH*    | The file path in which to store browser data (profiles, caches, etc.)                                          | /tmp/chrome                   |
| *BROWSER_HEIGHT*               | The height (in pixels) of the web browser used to take screenshots                                             | 1050                          |
| *BROWSER_WIDTH*                | The width (in pixels) of the web browser used to take screenshots                                              | 1680                          |
| *CSV_COMMENT_SYMBOL*           | If this character is the first in a CSV row, the CSV row is considered a comment and not processed             | #                             |
| *EMAIL_ADDRESSES*              | A comma-separated list of the user's email addresses                                                           |                               |
| *INCLUDE_MANUAL_TRACKERS*      | Whether to take screnshots of trackers that require manual user interaction                                    | false                         |
| *IP_ADDRESSES*                 | A comma-separated list of the user's IP addresses                                                              |                               |
| *LOG_LEVEL*                    | The logging level for console output                                                                           | INFO                          |
| *OPEN_OUTPUT_DIRECTORY*        | Whether to open the output screenshot directory when execution is complete (not working in Docker, debug only) | false                         |
| *OUTPUT_DIRECTORY_NAME_FORMAT* | The name of the output directory to be created for the of the screenshots                                      | yyyy-MM-dd                    |
| *OUTPUT_DIRECTORY_PARENT_PATH* | The output location of for the new directory created for the screenshots, relative to the project root         | /tmp/screenshots              |
| *TIMEZONE*                     | The local timezone, used to retrieve the current date to name the output directory                             | UTC                           |
| *TRACKER_INPUT_FILE_PATH*      | The path to the input tracker definition CSV file                                                              | /tmp/screenshots/trackers.csv |
| *USE_HEADLESS_BROWSER*         | Whether to use a headless browser for screenshots, or a full browser                                           | false                         |

## Contributing

### Requirements

- [Apache Maven](https://maven.apache.org/download.cgi)
- [Google Chrome](https://www.google.com/chrome/)
- [Java (JDK 23+)](https://jdk.java.net/23/)

### Debugging Application

Using IntelliJ, and click on **Run**> **Edit Configurations** and add the environment variables for the application. Once done, open
the [ProfileScreenshotter.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/ProfileScreenshotter.java) and run the `main`
method from the IDE.

[Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/) is used to leverage the host's installed Google Chrome browser to take
screenshots. While we usually run in headless mode, this can be changed by updating the `USE_HEADLESS_BROWSER` value in
the [configuration](#configuration-options). This will cause a new browser instance to launch when taking a screenshot, and can be used for debugging
a new implementation.

### Implementing New Tracker Handlers

All supported private trackers have an implementation found in the [handler](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler)
package. To add a new one,
extend [AbstractTrackerHandler.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler/AbstractTrackerHandler.java), following
the convention from an existing implementation,
like [AitherHandler.java](./tracker-profiles-screenshots/src/main/java/net/zodac/tracker/handler/AitherHandler.java).
