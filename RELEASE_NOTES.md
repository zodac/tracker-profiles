## Not Removing Empty Error Directories

Previously, if a failed tracker execution passed then the error screenshot would be removed. And if this was the final screenshot in the `errors` directory, the directory would also be removed.
However, when running with multiple parallel threads this could lead to a race condition where an expected directory no longer exists. Instead, we made the dirs once.
If you never had errors with trackers, you may find an `errors` directory now where you didn't see one before. But this can be ignored if no errors actually occur.
This only applies if the `TAKE_SCREENSHOT_ON_ERROR` environment variable is set to **true**.