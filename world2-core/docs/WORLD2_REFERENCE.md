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
# Start/reuse World2 API + open headless page
./dev.sh world2 1 1000 0.2

# Stop API server (uses PID file + fallback process match)
./dev.sh stopworld2

# Restart API server + relaunch headless page
./dev.sh restartworld2 1 1000 0.2
```

When `tools/run-world2-headless.sh` starts a new API process, it records the PID at:

```text
build/world2-server-<port>.pid
```

## API Endpoints

- `GET /health`
- `GET /scenarios`
- `GET /run?scenario=1&steps=1000&dt=0.2`
- `GET /run.csv?scenario=1&steps=1000&dt=0.2`

## Headless UI

Use:

```text
http://127.0.0.1:8000/headless.html?world2Scenario=1&steps=1000&dt=0.2&world2Api=http://127.0.0.1:18082

http://127.0.0.1:8000/headless.html?world2Api=http://127.0.0.1:18082
```

### `world2Csv` vs `world2Scenario`

The headless page needs a data source. The message:

`Use world2Csv=... or world2Scenario=...`

means you must provide one of these query parameters:

- `world2Scenario=<id>`
	- Tells the UI to call the World2 API (`/run.csv`) and generate data for the selected scenario.
	- Also supports `steps` and `dt`.
	- Example:
		- `http://127.0.0.1:8000/headless.html?world2Scenario=1&steps=1000&dt=0.2&world2Api=http://127.0.0.1:18082`

- `world2Csv=<path-or-url>`
	- Tells the UI to load an existing CSV directly (local relative path or full `http(s)` URL).
	- Useful when plotting pre-generated runs.
	- Example (absolute URL):
		- `http://127.0.0.1:8000/headless.html?world2Csv=http://127.0.0.1:18082/run.csv?scenario=1%26steps=1000%26dt=0.2`

If both are provided, `world2Csv` takes priority.
