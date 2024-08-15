# Tracker Profiles

## Overview

This is a tool used to log in to private torrent websites and take a screenshot of the user's profile page.

## Running Application

First, copy the [trackers_example.csv](./tracker-profiles-screenshots/src/main/resources/trackers_example.csv) file and rename it to **trackers.csv**.
This file needs to be updated with your tracker's information. The
[AbstractTrackerHandler.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler/AbstractTrackerHandler.java) implementation for
each tracker is retrieved by the _trackerCode_ field within the CSV file.

Open the [ProfileScreenshotter.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/ProfileScreenshotter.java) and run the `main`
method.

## Configuration

Copy the [config_example.properties](./tracker-profiles-screenshots/src/main/resources/config_example.properties) file and rename it to
**config.properties**. It is configured with some default values which can are described below.

| Configuration Property      | Description                                                                                            | Default Value |
|-----------------------------|--------------------------------------------------------------------------------------------------------|---------------|
| *emailAddresses*            | A comma-separated list of the user's email addresses                                                   |               |
| *ipAddresses*               | A comma-separated list of the user's IP addresses                                                      |               |
| *outputDirectoryNameFormat* | The name of the output directory to be created for the of the screenshots                              | yyyy-MM       |
| *outputDirectoryParentPath* | The output location of for the new directory created for the screenshots, relative to the project root | ./screenshots |
| *previewTrackerScreenshot*  | Whether to provide a pop-up preview of the screenshot once it has been taken                           | false         |
| *timeZone*                  | The local timezone, used to retrieve the current date to name the output directory                     | UTC           |
| *useHeadlessBrowser*        | Whether to use a headless browser for screenshots, or a full browser                                   | false         |

## Implementing New Tracker Handlers

All supported private trackers have an implementation found in the [handler](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler)
package. To add a new one,
extend [AbstractTrackerHandler.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler/AbstractTrackerHandler.java), following the
convention from an existing implementation,
like [AthHandler.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler/AthHandler.java).

[Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/) is used to leverage the host's installed Google Chrome browser to take
screenshots. While we usually run in headless mode, this can be updated in the [Configuration](#configuration). This will cause a new browser
instance to launch when taking a screenshot, and can be used for debugging a new implementation.
