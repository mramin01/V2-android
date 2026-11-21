# app/libs — sing-box libbox AAR goes here

This directory is intentionally empty in the repository.

`libbox.aar` is the Android binding of the sing-box core, built from the
sing-box source tree with gomobile — it is **not** published on Maven
Central, so Gradle cannot fetch it. Build it once with:

```
SING_BOX_VERSION=v1.11.10 ./scripts/build-libbox.sh
```

and copy the result here as `libbox.aar` (all-ABI build is ~93 MB).
The Gradle build then automatically:

- adds `src/libbox/java` to the main source set, and
- adds this AAR as a dependency.

Without the AAR the app still builds and runs: the full UI, config
manager, real TCP ping and subscription handling all work, but the power
button refuses to connect and shows "VPN core not ready" instead of
faking a connection. See `README.md` ("VPN core status") for details.
