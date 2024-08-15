# Tracker Profiles

## Overview

This is a tool used to log in to private torrent websites and take a screenshot of the user's profile page.

## Running Application

Open the [ProfileScreenshotter.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/ProfileScreenshotter.java) and run the `main`
method.

## Configuration

### Environment Variables

The following two environment variables need to be set, in order to redact sensitive information from the screenshot.

| Environment Variable | Description          |
|----------------------|----------------------|
| *EMAIL_ADDRESS*      | User's email address |
| *HOST_IP_ADDRESS*    | User's IP address    |

### Trackers

Copy the [trackers_example.csv](./tracker-profiles-screenshots/src/main/resources/trackers_example.csv) file and rename it to **trackers.csv**. Add
your tracker's information here.

### Output Directory

Each screenshot will be stored in a directory called `screenshots` in the root of this project.

## Supporting New Trackers

All supported private trackers have an implementation found in the [handler](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler)
package. To add a new one, extend [TrackerHandler.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/framework/TrackerHandler.java),
following the convention from an existing implementation,
like [AthHandler.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/handler/AthHandler.java).

[Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/) is used to leverage the host's installed Google Chrome browser to take
screenshots. While we usually run in headless mode, this can be updated
in [ProfileScreenshotter.java](./tracker-profiles-screenshots/src/main/java/me/zodac/tracker/ProfileScreenshotter.java), by commenting out the
following line:

```java
    chromeOptions.addArguments("--headless=new");
```

This will cause a new browser instance to launch when taking a screenshot, and can be used for debugging a new implementation.
