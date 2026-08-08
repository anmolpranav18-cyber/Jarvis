# Jarvis — Android wake-word assistant

Says "Jarvis" wakes it up, even with the screen off. This is a real Android
Studio project, not a demo — but it needs a few setup steps before it'll
build and run on your phone.

## What it does
- Runs as a background service, always listening for the word "Jarvis"
- On wake, listens for your command, speaks a response back
- Handles a few built-in commands (lights, thermostat, time) — see
  `CommandProcessor.kt` to add your own or wire up a real AI API
- Restarts itself automatically after a reboot if you'd left it on

## What it can't do
- Work while the phone is fully powered off (no software runs without power)
- Run on iPhone — iOS doesn't allow apps to listen in the background
- Control real smart home devices out of the box — the light/thermostat
  commands are placeholders you'll wire up to your actual devices later

## Setup (about 15 minutes)

1. **Install Android Studio** (free, from developer.android.com) if you
   don't have it.

2. **Get a free Picovoice access key**
   - Sign up at console.picovoice.ai
   - Copy your AccessKey
   - Open `app/src/main/java/com/jarvis/app/WakeWordService.kt`
   - Replace `"YOUR_PICOVOICE_ACCESS_KEY"` with your key
   - (Free tier covers personal use; don't commit this key to a public repo)

3. **Open the project**
   - Android Studio → Open → select the `JarvisApp` folder
   - Let it sync Gradle (needs internet the first time, to download
     dependencies)

4. **Run it**
   - Plug in your Android phone (enable Developer Options → USB debugging),
     or use an emulator
   - Click Run
   - Grant microphone and notification permissions when asked
   - Tap "Activate Jarvis"
   - Lock your phone, say "Jarvis" — it should respond

## Battery note
Continuous wake-word listening does use some battery, same as "Hey Google"
does. Some phone manufacturers (Samsung, Xiaomi, etc.) aggressively kill
background services to save power — you may need to disable battery
optimization for this app in Settings for it to stay running reliably.

## Making it actually smart
Right now commands are handled by simple keyword matching in
`CommandProcessor.kt`. To make it understand open-ended requests like a
real assistant, replace the `process()` function with a call to an LLM API
(e.g. the Anthropic API) — you'll need an API key and network access, and
you should load the key from a local, non-committed file rather than
hardcoding it in source.
