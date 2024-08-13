# Tracker Profiles

## Overview

This is a tool used to log in to private torrent websites and take a screenshot of the user's profile page.

## Running Application

Open the [ProfileScreenshotter.java](./profile/src/main/java/me/zodac/tracker/ProfileScreenshotter.java) and run the **main** method.

## Configuration

### Environment Variables

The following two environment variables need to be set, in order to redact sensitive information from the screenshot:

| Environment Variable | Description          |
|----------------------|----------------------|
| *EMAIL_ADDRESS*      | User's email address |
| *HOST_IP_ADDRESS*    | User's IP address    |

### Trackers

Copy the [trackers_example.csv](./profile/src/main/resources/trackers_example.csv) file and rename it to **trackers.csv**. Add your tracker's
information here.

### Output Directory

Each screenshot will be stored in a directory **screenshots** in the root of this project.

## Supporting New Trackers

All supported private trackers have an implementation found in the [handler](./profile/src/main/java/me/zodac/tracker/handler) package. To add a new
one, extend [TrackerHandler.java](./profile/src/main/java/me/zodac/tracker/framework/TrackerHandler.java), following the convention from an existing
implementation like [AitherHandler.java](./profile/src/main/java/me/zodac/tracker/handler/AitherHandler.java).

[Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/) is used to leverage the host's installed Google Chrome browser to take
screenshots. While we usually run in headless mode, this can be updated
in [Executor.java](./profile/src/main/java/me/zodac/tracker/util/Executor.java), by commenting out the following line:

```java
    chromeOptions.addArguments("--headless=new");
```

This will cause a new browser instance to launch when taking a screenshot, and can be used for debugging a new implementation.
