# AGENTS: How to be productive in this codebase

Purpose: quick, focused instructions for AI coding agents to become productive here.

Checklist for an agent (follow these steps in order)
- Read `src/main/java/umg/edu/gt/floristeria/Main.java` to understand runtime modes.
- Inspect `hash/CustomHashTable` and `src/test/**/CustomHashTableTest.java` for core data-structure invariants.
- Inspect `service/*`, `graph/*`, and `api/GraphRestApi.java` to understand data flows and integration points.
- Run the build and tests locally before making changes.

Quick commands (Windows PowerShell)
- Build: `mvn package`
- Run CLI demo (default main): `mvn exec:java`
- Run CLI demo with synthetic source: `mvn exec:java -Dexec.args="--source=synth"`
- Run GUI (JavaFX): `mvn javafx:run` or `mvn exec:java -Dexec.args="--gui"`
- Run tests: `mvn test`

Environment & runtime notes
- Java target is `release 25` (see `pom.xml`). Use a JDK compatible with Java 25 to compile/run.
- Oracle DB integration: classes read `ORACLE_URL`, `ORACLE_USER`, `ORACLE_PASS` from the environment.
  Example (PowerShell):
  $env:ORACLE_URL = "jdbc:oracle:thin:@host:1521/servicename"; $env:ORACLE_USER = "user"; $env:ORACLE_PASS = "pass"; mvn exec:java
- REST API: `GraphRestApi` starts an HTTP server on port `8085` and serves `web/index.html` from the working directory. Ensure `web/index.html` exists if you plan to use the frontend.

Big-picture architecture (why things are organized this way)
- Core data-structure: `hash/CustomHashTable` — used as the in-memory catalog for items and providers. Tests define invariants: initial capacity = 101, rehash threshold = 0.75, replacement of existing key does NOT increase collision count. See `src/test/.../CustomHashTableTest.java` for examples (IDs that collide: 1, 102, 203...).
- Data sources: `service/CatalogoSource` implementations
  - `SyntheticCatalogoSource` (in-memory synthetic items; IDs start at `1000`) — fast to use in dev and CI.
  - `DatabaseCatalogoSource` (JDBC/Oracle) — production pipeline; it propagates SQLExceptions for upper layers to handle. Factory `CatalogoSources.defaultSource(...)` auto-detects Oracle when `ORACLE_URL` is defined.
- Reporting: `service/ReportService` builds report rows; `service/ReportExporter` writes CSV/JSON to `reports/4.1_*.{csv,json}`.
- Graph & REST: `graph/CommercialGraph` executes SQL queries directly using the same Oracle env vars and produces `Nodo`/`Arista` lists; `api/GraphRestApi` exposes those as JSON for the frontend and sets permissive CORS headers.
- UI: JavaFX app under `ui/` (`HashTableApp`, launched via `HashTableLauncher` to avoid JavaFX module classloader issues).

Project-specific conventions & patterns
- Env-var auto-detect: presence of `ORACLE_URL` switches behavior. Use `CatalogoSources.oracleConfigurado()` to check.
- DB credential handling: prefer `DatabaseCatalogoSource.fromEnv()` which throws a clear IllegalStateException if variables are missing — useful for quick fail-fast behavior.
- JavaFX launcher pattern: use `ui/HashTableLauncher` as the main class in IDE runs to avoid JavaFX module errors (explained in the class javadoc).
- File/working-dir assumptions: `GraphRestApi` serves `web/index.html` using relative path `web/index.html` — run from the project root or set working dir accordingly.
- Test assumptions: unit tests rely on deterministic collisions and threshold behavior (do not randomly change `CustomHashTable` internals without updating tests).

Important files to inspect when changing behavior
- `src/main/java/umg/edu/gt/floristeria/hash/CustomHashTable.java` — hashing, probes, collision counting, rehash behavior.
- `src/test/java/umg/edu/gt/floristeria/hash/CustomHashTableTest.java` — demonstrates expected behavior and edge cases (Integer.MIN_VALUE hash, probes reporting).
- `src/main/java/umg/edu/gt/floristeria/service/DatabaseCatalogoSource.java` — JDBC queries and error propagation.
- `src/main/java/umg/edu/gt/floristeria/graph/CommercialGraph.java` — three graph SQL queries used by the REST API.
- `src/main/java/umg/edu/gt/floristeria/api/GraphRestApi.java` — JSON shape used by Vis.js frontend and CORS behavior.
- `pom.xml` — compiler target (Java 25), JavaFX plugin, and exec plugin configuration.

Testing & debugging tips for agents
- Use `mvn -DskipTests=false test` to run unit tests and catch regressions; tests are focused on `CustomHashTable`.
- To iterate quickly without DB, set `--source=synth` or ensure `ORACLE_URL` is unset so the project defaults to synthetic data.
- When changing SQL in `CommercialGraph`, be aware the class uses `Class.forName("oracle.jdbc.driver.OracleDriver")` in `construirGrafoTrazabilidad` as an extra check; DatabaseCatalogoSource relies on the ojdbc11 dependency (SPI auto-registration).

What the REST API returns (useful sample)
- JSON shape: { "nodes": [{"id":"...","label":"...","group":"..."}], "edges": [{"from":"...","to":"...","label":"..."}] }
  Consult `api/GraphRestApi.java` and `graph/CommercialGraph.java` for the attribute names.

If you must stop the REST server during a long-running CI job
- `Main` starts `GraphRestApi.iniciarServidor()` at the end of `main`. To avoid starting the HTTP server in a run, either:
  - Run `mvn exec:java -Dexec.mainClass=umg.edu.gt.floristeria.ui.HashTableLauncher` to run the UI launcher instead; or
  - Run tests only (`mvn test`) which do not start the server; or
  - Modify `Main` to make server start conditional (suggested change: add a `--no-api` flag) if you will maintain the repo.

Contact pointers inside code (use these anchors when asking maintainers)
- `Main.java` — runtime orchestration and demo flow.
- `DatabaseCatalogoSource.fromEnv()` — env-var checks and error messages.
- `HashTableLauncher.java` — explains JavaFX launcher pattern.

End.

