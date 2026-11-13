## Making Cloudflare Checks Headless

I've updated the project so any website that requires a Cloudflare verification check on login can now be performed headless, instead of needing the user to manually click it.

Any website which has other checks (Captcha, 2FA, questions) will still be considered `MANUAL`.
