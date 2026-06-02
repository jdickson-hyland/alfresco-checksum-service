# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

All development operations go through `run.sh` (or `run.bat` on Windows):

```bash
./run.sh build_start          # Full build + start all Docker services + tail logs
./run.sh build_start_it_supported  # Same as above, but also prepares IT test jars
./run.sh start                # Start already-built Docker environment
./run.sh stop                 # Stop all containers
./run.sh purge                # Stop and delete all Docker volumes (wipes data)
./run.sh reload_acs           # Rebuild platform module + restart ACS container only
./run.sh reload_share         # Rebuild share module + restart Share container only
./run.sh build_test           # Full build + start + run ITs + tear down
./run.sh test                 # Run integration tests against already-running environment
```

Direct Maven commands:
```bash
mvn clean package             # Build all modules
mvn test -pl alfresco-checksum-service-platform  # Run unit tests only
mvn verify -pl alfresco-checksum-service-platform,alfresco-checksum-service-integration-tests  # Run ITs (requires running Docker env)
```

## Service Ports

| Service | Port |
|---|---|
| ACS (Alfresco Content Services) | 8080 |
| Alfresco Share | 8180 |
| PostgreSQL | 5555 |
| Solr (ASS) | 8983 |
| ActiveMQ Web Console | 8161 |
| ACS debug | 8888 |
| Share debug | 9898 |

## Architecture

This is an **Alfresco SDK 4.12.0 All-In-One (AIO)** project targeting ACS 25.2.0 community edition. The extension runs as JARs deployed inside custom Docker images — there are no WAR deployments or runner projects. The entire environment (ACS, Share, Solr, PostgreSQL, ActiveMQ) is managed via Docker Compose at `docker/docker-compose.yml` (filtered to `target/classes/docker/docker-compose.yml` at build time).

### Module Structure

- **`alfresco-checksum-service-platform`** — The ACS backend JAR. Java code lives in `src/main/java/org/hyland/`. Spring context files, content models, web script descriptors/templates, and workflows live under `src/main/resources/alfresco/module/alfresco-checksum-service-platform/`.
- **`alfresco-checksum-service-share`** — The Alfresco Share UI JAR. Share-specific Spring context and config under `src/main/resources/alfresco/web-extension/`.
- **`alfresco-checksum-service-integration-tests`** — Integration tests that run against the live Docker environment. Test classes follow the `*IT.java` naming convention and are executed by maven-failsafe-plugin. Tests hit `http://localhost:8080/alfresco` by default (override with `-Dacs.endpoint.path=...`).
- **`alfresco-checksum-service-platform-docker`** / **`alfresco-checksum-service-share-docker`** — Docker image build modules; they collect the platform/share JARs and inject them into the base ACS/Share images.

### Checksum Service Implementation

The checksum service is implemented in package `org.hyland.checksum` and consists of:

- **`ChecksumModel`** — QName constants for the `cs:checksumable` aspect and its three properties (`cs:checksum`, `cs:checksumAlgorithm`, `cs:checksumLastUpdated`). Namespace: `http://www.hyland.org/model/checksum/1.0`.
- **`ChecksumService` / `ChecksumServiceImpl`** — Computes SHA-256 (configurable) digests, applies the `cs:checksumable` aspect, and queries for duplicates using CMIS SQL with `QueryConsistency.TRANSACTIONAL` (database-only, no Solr dependency).
- **`ChecksumBehavior`** — `OnContentUpdatePolicy` bound to `cm:content` with `TRANSACTION_COMMIT` frequency. Fires after metadata extraction; sets checksum properties last so they cannot be wiped. Optionally rejects duplicates.
- Three web script controllers exposing: `GET /checksum/duplicates`, `GET /checksum/node/{store}/{id}/duplicates`, `GET /checksum/node/{store}/{id}/validate`.

Content model: `alfresco-checksum-service-platform/src/main/resources/alfresco/module/alfresco-checksum-service-platform/model/checksum-model.xml`  
Spring beans: `…/context/checksum-context.xml` (imported from `module-context.xml`)

### Spring Wiring (Platform)

Spring context is bootstrapped from `module-context.xml`, which imports files in order:
1. `bootstrap-context.xml` — content model registration and workflow deployment (must load first so models exist before services reference them)
2. `service-context.xml` — application service beans
3. `webscript-context.xml` — web script Java controller beans
4. `checksum-context.xml` — checksum service beans (added for this project)

Bean IDs for web scripts must follow the convention `webscript.<package>.<name>.<httpmethod>` and extend `webscript` parent bean.

**Spring property placeholders:** Module properties in `alfresco/module/{id}/alfresco-global.properties` (inside the JAR) are not reliably loaded by Spring's `PropertyPlaceholderConfigurer`. Always use the `:defaultValue` syntax in Spring XML (e.g., `${checksum.algorithm:SHA-256}`) AND add the properties to `alfresco-checksum-service-platform-docker/src/main/docker/alfresco-global.properties`, which is the file Spring actually loads in the Docker container.

### Web Scripts

Web scripts live in `src/main/resources/alfresco/extension/templates/webscripts/`. Each web script is a set of co-located files sharing the same base name:
- `*.desc.xml` — descriptor (URL, auth, format)
- `*.get.js` — optional JavaScript controller
- `*.get.html.ftl` — FreeMarker HTML template
- Java controller (in `src/main/java`) — extends `DeclarativeWebScript`, registered as a Spring bean

### Behavior Policies — Critical Gotcha

**Always use `TRANSACTION_COMMIT` (not `EVERY_EVENT`) for behaviors that set node properties.** Alfresco's metadata extraction pipeline (`AbstractMappingMetadataExtracter`) runs mid-transaction after content is written and calls `NodeService.setProperties()` with a map of `cm:*` properties. This wipes any aspect properties (e.g., `cs:checksum`) that were set during an `EVERY_EVENT` behavior because they are not included in that map. `TRANSACTION_COMMIT` fires in `beforeCommit()` after all pipeline operations have finished, so properties set there are the final values in the transaction. Throwing from `TRANSACTION_COMMIT` still rolls back the transaction (useful for duplicate rejection).

**`CMIS` queries for database-only searches:** Use `SearchService.LANGUAGE_CMIS_ALFRESCO` with `QueryConsistency.TRANSACTIONAL`. The `QueryConsistency` class is at `org.alfresco.service.cmr.search.QueryConsistency` (not `org.alfresco.repo.search.impl.querymodel`).

### Share UI Configuration

The correct approach (verified against running Share 25.2.0 and the `alfresco-smartfolder-manager` reference project):

1. Place module config in a **uniquely-named file**: `alfresco-checksum-service-share/src/main/resources/alfresco/web-extension/share-config-checksum-service.xml`
2. Leave `META-INF/share-config-custom.xml` as an empty stub (`<alfresco-config/>`) — do not put module config there.
3. Override the `webframework.configsource` bean in `alfresco-checksum-service-share-slingshot-application-context.xml`, preserving all original sources from `slingshot-application-context.xml` and appending the module config as the last entry:

   ```xml
   <value>classpath:alfresco/web-extension/share-config-checksum-service.xml</value>
   ```

The full verified source list for Share 25.2.0 is already in the slingshot application context file.

### Logging

The module's `log4j2.properties` inside the JAR is **not loaded** by the ACS container at runtime. To add logging for a package, add entries to `alfresco-checksum-service-platform-docker/src/main/docker/dev-log4j2.properties`:

```properties
logger.checksum.name=org.hyland.checksum
logger.checksum.level=debug
```

Run `./run.sh reload_acs` after changing this file.

### Testing

- **Unit tests** (`src/test/java` in the platform module): pure JUnit 4 + Mockito, no running container needed. Run with `mvn test`.
- **Integration tests** (`alfresco-checksum-service-integration-tests`): In-container tests using `@RunWith(AlfrescoTestRunner.class)` extending `AbstractAlfrescoIT`. The `@Remote` annotation in alfresco-rad 4.12.0 has no `runAs()` attribute and cannot be applied to methods — do not use it. Create nodes via `NodeService.createNode()` with `ContentModel.ASSOC_CONTAINS`. Require `./run.sh build_start_it_supported` before first run.

### Hot Reloading

JRebel is configured and `rebel.xml` is generated for each module during `process-resources`. To use it, set up a JRebel license and agent; the Docker images include `hotswap-agent.properties` for class reloading without container restarts.

### Maven Repositories

Dependencies resolve from `artifacts.alfresco.com`. Enterprise artifacts (from `alfresco-private-repository`) require credentials in `~/.m2/settings.xml`.
