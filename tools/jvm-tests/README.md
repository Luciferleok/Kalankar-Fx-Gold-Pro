# JVM logic tests (V37-A)

These tests check the app's decision, ledger and display logic on a normal computer, without an Android device.
They run on every push in the **Build Android APK** workflow; if one fails, no APK is built.

- `tests/` — one small Kotlin program per release (V32 … V36-B), each with `main()`. All data is **synthetic**.
- `stubs/` — tiny stand-ins for Android-only libraries so the logic compiles on the JVM.
- `fixtures/` — fixed reference data some tests compare against.
- `run.sh` — compiles the app logic, runs every test in parallel, and fails if any test fails.

Run locally: `KOTLIN_HOME=/path/to/kotlinc tools/jvm-tests/run.sh` (Kotlin compiler 2.0.21).

What these tests do **not** prove: that the Compose screens look right on a phone, or that predictions are accurate
in the real market. Phone screens are verified by the owner's screenshots; accuracy only by forward results.
