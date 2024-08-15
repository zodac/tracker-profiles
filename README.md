# Tracker Profiles

## Overview

This is a tool used to log in to private torrent websites and take a screenshot of the user's profile page.

## Running Application

Open the [ProfileScreenshotter.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/ProfileScreenshotter.java) and run the `main`
method.

## Configuration

Copy the [config_example.properties](./tracker-profiles-screenshots/src/main/resources/config_example.properties) file and rename it to *
*config.properties**. It is configured with some default values which can be updated as below.

| Configuration Property     | Description                                                                  | Default Value |
|----------------------------|------------------------------------------------------------------------------|---------------|
| *emailAddresses*           | A comma-separated list of the user's email addresses                         |               |
| *ipAddresses*              | A comma-separated list of the user's IP addresses                            |               |
| *outputDirectoryPath*      | The output location of the screenshots, relative to the project root         | ./screenshots |
| *previewTrackerScreenshot* | Whether to provide a pop-up preview of the screenshot once it has been taken | false         |
| *useHeadlessBrowser*       | Whether to use a headless browser for screenshots, or a full browser         | false         |

## Trackers

Copy the [trackers_example.csv](./tracker-profiles-screenshots/src/main/resources/trackers_example.csv) file and rename it to **trackers.csv**. Add
your tracker's information here.

## Implementing New Tracker Handlers

All supported private trackers have an implementation found in the [handler](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler)
package. To add a new one, extend [TrackerHandler.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/framework/TrackerHandler.java),
following the convention from an existing implementation,
like [AthHandler.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler/AthHandler.java).

[Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/) is used to leverage the host's installed Google Chrome browser to take
screenshots. While we usually run in headless mode, this can be updated in the [Configuration](#configuration). This will cause a new browser
instance to launch when taking a screenshot, and can be used for debugging a new implementation.
