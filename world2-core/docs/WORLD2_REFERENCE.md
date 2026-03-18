# World2 Reference

## Modules

- `world2-core`: scenario library, simulator, result data model
- `world2-server`: HTTP API for running scenarios and exporting CSV

## Resource Files

Located in `world2-core/src/main/resources/world2/`:

- `functions_table_default.json`
- `functions_switch_default.json`
- `scenarios/index.csv`
- `scenarios/functions_switch_scenario_<id>.json`
- `scenarios/functions_table_scenario_4.json`

## Run Server

```bash
./gradlew :world2-server:run --args="18082"
```

## Dev Script Shortcuts

```bash
# Start/reuse World2 API + open World2 UI page
./dev.sh world2 1 1000 0.2

# Stop API server (uses PID file + fallback process match)
./dev.sh stopworld2

# Restart API server + relaunch World2 UI page
./dev.sh restartworld2 1 1000 0.2
```

When `tools/run-world2-ui.sh` starts a new API process, it records the PID at:

```text
build/world2-server-<port>.pid
```

## API Endpoints

- `GET /health`
- `GET /scenarios`
- `GET /run?scenario=1&steps=1000&dt=0.2`
- `GET /run.csv?scenario=1&steps=1000&dt=0.2`

## World2 UI

Use:

```text
http://127.0.0.1:8000/world2.html?world2Csv=http%3A%2F%2F127.0.0.1%3A18082%2Frun.csv%3Fscenario%3D1%26steps%3D1000%26dt%3D0.2
```

### `world2Csv`

The World2 UI page needs a data source. The message:

`Use world2Csv=...`

means you must provide this query parameter:

- `world2Csv=<path-or-url>`
	- Tells the UI to load an existing CSV directly (local relative path or full `http(s)` URL).
	- Useful when plotting pre-generated runs.
	- Example (absolute URL, URL-encoded):
		- `http://127.0.0.1:8000/world2.html?world2Csv=http%3A%2F%2F127.0.0.1%3A18082%2Frun.csv%3Fscenario%3D1%26steps%3D1000%26dt%3D0.2`

`./dev.sh world2 ...` now emits the `world2Csv=...` URL form by default.
