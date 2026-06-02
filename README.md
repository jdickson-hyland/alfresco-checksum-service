# Alfresco Checksum Service

An Alfresco Content Services (ACS) extension that automatically computes, stores, and validates content checksums on every upload or update. It provides duplicate detection with optional rejection, and a REST API for querying duplicates across the repository or per node.

## Content Model

The module defines a `cs:checksumable` aspect (namespace `http://www.hyland.org/model/checksum/1.0`) applied automatically to every `cm:content` node on upload:

| Property | Type | Description |
| --- | --- | --- |
| `cs:checksum` | `d:text` | Hex digest of the content stream |
| `cs:checksumAlgorithm` | `d:text` | Algorithm used (e.g. `SHA-256`) |
| `cs:checksumLastUpdated` | `d:datetime` | Timestamp of last computation |

Supported algorithms: `MD5`, `SHA-1`, `SHA-256`, `SHA-512` (any value accepted by `java.security.MessageDigest`). Configured via `checksum.algorithm` in `alfresco-global.properties` (default: `SHA-256`).

## Services

| Bean | Description |
| --- | --- |
| `checksumService` | Computes digests, applies the aspect, finds duplicates, validates stored vs. computed checksum |
| `checksumBehavior` | `OnContentUpdatePolicy` bound to `cm:content`; fires at `TRANSACTION_COMMIT` to avoid conflict with Alfresco's metadata extraction pipeline |

Set `checksum.duplicates.reject=true` in `alfresco-global.properties` to reject duplicate uploads with a transaction rollback.

## REST API Endpoints

All endpoints require authentication. Base path: `http://localhost:8080/alfresco/service`

### List all duplicate groups

```http
GET /checksum/duplicates
```

Returns all sets of nodes that share identical content:

```json
{
  "duplicateGroups": [
    {
      "checksum": "874722bc...",
      "algorithm": "SHA-256",
      "count": 2,
      "nodes": [
        { "nodeRef": "workspace://SpacesStore/abc", "name": "file.pdf", "path": "/Company Home/..." },
        { "nodeRef": "workspace://SpacesStore/def", "name": "file-copy.pdf", "path": "/Company Home/..." }
      ]
    }
  ]
}
```

### List duplicates of a specific node

```http
GET /checksum/node/{store_type}/{store_id}/{id}/duplicates
```

Example: `GET /checksum/node/workspace/SpacesStore/{uuid}/duplicates`

Returns 404 if the node does not exist, 400 if it does not have the `cs:checksumable` aspect.

```json
{
  "nodeRef": "workspace://SpacesStore/{uuid}",
  "checksum": "874722bc...",
  "algorithm": "SHA-256",
  "duplicates": [
    { "nodeRef": "workspace://SpacesStore/def", "name": "file-copy.pdf", "path": "/Company Home/..." }
  ]
}
```

### Validate checksum of a specific node

```http
GET /checksum/node/{store_type}/{store_id}/{id}/validate
```

Recomputes the digest and compares it to the stored value. Returns `"valid": false` if the content has been modified outside of Alfresco's normal upload path.

```json
{
  "nodeRef": "workspace://SpacesStore/{uuid}",
  "valid": true,
  "algorithm": "SHA-256",
  "stored": "874722bc...",
  "computed": "874722bc..."
}
```

---

## Alfresco AIO Project - SDK 4.11.0

This is an All-In-One (AIO) project for Alfresco SDK 4.11.0.

Run with `./run.sh build_start` or `./run.bat build_start` and verify that it

 * Runs Alfresco Content Service (ACS)
 * Runs Alfresco Share
 * Runs Alfresco Search Service (ASS)
 * Runs PostgreSQL database
 * Deploys the JAR assembled modules
 
All the services of the project are now run as docker containers. The run script offers the next tasks:

 * `build_start`. Build the whole project, recreate the ACS and Share docker images, start the dockerised environment composed by ACS, Share, ASS and 
 PostgreSQL and tail the logs of all the containers.
 * `build_start_it_supported`. Build the whole project including dependencies required for IT execution, recreate the ACS and Share docker images, start the 
 dockerised environment composed by ACS, Share, ASS and PostgreSQL and tail the logs of all the containers.
 * `start`. Start the dockerised environment without building the project and tail the logs of all the containers.
 * `stop`. Stop the dockerised environment.
 * `purge`. Stop the dockerised container and delete all the persistent data (docker volumes).
 * `tail`. Tail the logs of all the containers.
 * `reload_share`. Build the Share module, recreate the Share docker image and restart the Share container.
 * `reload_acs`. Build the ACS module, recreate the ACS docker image and restart the ACS container.
 * `build_test`. Build the whole project, recreate the ACS and Share docker images, start the dockerised environment, execute the integration tests from the
 `integration-tests` module and stop the environment.
 * `test`. Execute the integration tests (the environment must be already started).

# Few things to notice

 * No parent pom
 * No WAR projects, the jars are included in the custom docker images
 * No runner project - the Alfresco environment is now managed through [Docker](https://www.docker.com/)
 * Standard JAR packaging and layout
 * Works seamlessly with Eclipse and IntelliJ IDEA
 * JRebel for hot reloading, JRebel maven plugin for generating rebel.xml [JRebel integration documentation]
 * AMP as an assembly
 * Persistent test data through restart thanks to the use of Docker volumes for ACS, ASS and database data
 * Integration tests module to execute tests against the final environment (dockerised)
 * Resources loaded from META-INF
 * Web Fragment (this includes a sample servlet configured via web fragment)

# TODO

  * Abstract assembly into a dependency so we don't have to ship the assembly in the archetype
  * Functional/remote unit tests