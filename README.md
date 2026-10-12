# ToyClimate

An Android app that starts your Toyota's climate control at scheduled times, based on the weather outside:

- colder than the heating threshold (default 12°C) → heats to 22°C, with heated windshield and rear window (a switch in Settings, on by default);
- warmer than the cooling threshold (default 24°C) → cools to 20°C;
- in between → nothing starts.

Schedules: add as many as you like in **Settings → Schedule**. Each one has the days you tick (for example Tuesday
and Thursday), its own start times and an on/off switch. One-time starts run once on a date and time you pick.
The default schedule is Monday–Friday at 06:20 and 14:30.

## Install

1. Download `toyota-clima.apk` from [Releases → latest](../../releases/tag/latest).
2. Open it on your phone and allow installing from unknown sources.
3. In **Settings → Toyota account**, enter your email and password. Everything saves automatically.
4. If an orange notice shows on **Home**, tap **Allow** and **Turn off** (exact alarms, battery optimization).
5. In **Settings → Test**, tap **Test**: it checks the weather and your Toyota account without starting the car.
6. Turn on the switch in the blue panel on **Home**.

## Pages

Open them from the ☰ menu at the top left.

- **Home**: the next start and the month calendar. Filled days follow your weekly schedule, outlined days have a
  calendar event, and a dot marks the days you changed. Tap a day to open its list: the climate starts, every
  Google Calendar event that day, what the log recorded, a switch to skip your schedules on that date only, and a
  button to add a one-time start.
- **Settings**: Toyota account, schedules and one-time starts, temperatures, calendar events and test.
- **Log**: every start and every test.

## Google Calendar events

Turn on **Settings → Calendar events** and allow calendar access. Any event whose title contains your chosen word
(default “mașina”; case and diacritics don't matter) starts the climate 5 minutes before the event time, using the
same temperature rules. All-day events are ignored, and events within 15 minutes of a scheduled start don't start
the climate a second time. The app re-checks the calendar every half hour, so an event added on another device is
picked up once it syncs to the phone.

Waze doesn't let other apps read its planned drives, but it reads Google Calendar too.

## Requirements and limits

- The car needs remote climate control in MyToyota (Remote Services active).
- The MyToyota account can't use two-step verification.
- The Toyota API is unofficial, ported from [pytoyoda](https://github.com/pytoyoda/pytoyoda). Toyota can change it at any time.
- The password stays in the app's private storage on the phone and is only sent to Toyota's servers.
- A start is rejected if the car is unlocked, a door is open, the key is inside or the climate is already running.

## Build

Every push to `main` builds the APK with GitHub Actions and publishes it to the `latest` release.
The project has no external dependencies (Android SDK and the Kotlin standard library only).

`tools/toyota_clima.py` is a Python version of the same logic, handy for testing from a computer.
