# material-edc

`material-edc` is the Materials Commons EDC distribution launcher. It combines
the EDC control plane with:

- centralized OAuth2/JWKS authentication for the Management API;
- OAuth2 client-credentials authorization for Data Plane Signaling; and
- the SDK-backed HTTP pull dataplane from
  `SignalingDataPlaneRuntimeExtension`.

## Launcher dependencies

The launcher is assembled from these modules:

| Dependency | Purpose |
| --- | --- |
| `dist:bom:controlplane-base-bom` | EDC boot/runtime, classic control plane, DSP, HTTP server, OAuth2 client and management APIs |
| `data-protocols:data-plane-signaling:data-plane-signaling-core` | Data Plane Signaling API and protocol support |
| `system-tests:e2e-transfer-test:signaling-data-plane` | SDK-backed signaling dataplane, including `Finite-PULL` and `NonFinite-PULL` |
| `extensions:common:api:management-api-oauth2-authentication` | JWT validation for the Management API using the central IdP issuer and JWKS |
| `extensions:common:iam:iam-mock` | Classic DSP `IdentityService` required by the current control-plane stack |

The IAM mock is only a control-plane compatibility provider. It is not used to
authenticate the Management API or the dataplane; those use the central OAuth2
configuration below.

## Build

From `EDC-Connector`:

```bash
./gradlew :material-edc:build
```

The application distribution is written to
`material-edc/build/install/material-edc`.

## Configuration

EDC accepts environment variables by converting the name to lowercase and
replacing underscores with dots. For example:

```text
EDC_IAM_OAUTH2_JWKS_URL -> edc.iam.oauth2.jwks.url
DATAPLANE_AUTHORIZATION_JWKS_URI -> dataplane.authorization.jwks.uri
```

The important runtime settings are:

| Environment variable | Required | Description |
| --- | --- | --- |
| `EDC_PARTICIPANT_ID` | yes | Participant identifier for this connector |
| `WEB_HTTP_PORT` | no | Default HTTP server port; defaults to `8181` |
| `WEB_HTTP_PATH` | no | Default API context; use `/api` |
| `WEB_HTTP_MANAGEMENT_PATH` | no | Management API path; use `/management` |
| `WEB_HTTP_PROTOCOL_PATH` | no | DSP protocol path; use `/protocol` |
| `DATAPLANE_ID` | yes | Dataplane registration identifier |
| `DATAPLANE_AUTHORIZATION` | no | Signaling authorization profile; defaults to `oauth2_client_credentials` |
| `DATAPLANE_AUTHORIZATION_JWKS_URI` | yes for OAuth2 signaling | JWKS endpoint used to validate signaling bearer tokens |
| `EDC_IAM_OAUTH2_ISSUER` | recommended | Expected issuer for Management API JWTs |
| `EDC_IAM_OAUTH2_JWKS_URL` | yes | JWKS endpoint used to validate Management API JWTs |
| `EDC_IAM_OAUTH2_JWKS_CACHE_VALIDITY` | no | JWKS cache duration in milliseconds; defaults to five minutes |
| `EDC_DSP_CALLBACK_ADDRESS` | deployment-dependent | Public DSP callback URL if it cannot be derived from the local HTTP settings |

`EDC_IAM_OAUTH2_ISSUER` and `EDC_IAM_OAUTH2_JWKS_URL` should refer to the same
central identity provider. Do not put private keys, client secrets or other
credentials in the image; supply them through the deployment secret mechanism.

## Run locally

The following is the minimum illustrative configuration; replace the identity
provider values with the real issuer and JWKS endpoint:

```properties
edc.participant.id=material-edc

web.http.port=8181
web.http.path=/api
web.http.management.path=/management
web.http.protocol.path=/protocol

dataplane.id=material-edc-http-pull
dataplane.authorization=oauth2_client_credentials
dataplane.authorization.jwks.uri=https://identity.example/.well-known/jwks.json

edc.iam.oauth2.issuer=https://identity.example/
edc.iam.oauth2.jwks.url=https://identity.example/.well-known/jwks.json
```

Start the generated launcher with the normal EDC configuration mechanism, for
example:

```bash
JAVA_OPTS="-Dedc.fs.config=/path/to/material-edc.properties" \
  ./material-edc/build/install/material-edc/bin/material-edc
```

The HTTP pull dataplane advertises the `Finite-PULL` and `NonFinite-PULL`
profiles. The signaling dataplane also advertises corresponding push and
asynchronous profiles; replace the signaling extension if the deployment must
expose pull-only capabilities.

## Docker

Build from the `EDC-Connector` directory so the Dockerfile can access the
multi-module Gradle project:

```bash
docker build -f material-edc/Dockerfile -t material-edc:local .
```

Run it with environment variables. The image supplies safe defaults for local
HTTP paths and ports, but the OAuth2/JWKS settings must be supplied by the
deployment:

```bash
docker run --rm --name material-edc \
  -p 8181:8181 \
  -e EDC_PARTICIPANT_ID=material-edc \
  -e DATAPLANE_ID=material-edc-http-pull \
  -e DATAPLANE_AUTHORIZATION=oauth2_client_credentials \
  -e DATAPLANE_AUTHORIZATION_JWKS_URI=https://identity.example/.well-known/jwks.json \
  -e EDC_IAM_OAUTH2_ISSUER=https://identity.example/ \
  -e EDC_IAM_OAUTH2_JWKS_URL=https://identity.example/.well-known/jwks.json \
  material-edc:local
```

For repeatable deployments, put the variables in an uncommitted `.env`
file and use `docker run --env-file .env ...`.

The image exposes port `8181`. With the defaults, health endpoints are:

```text
http://localhost:8181/api/liveness
http://localhost:8181/api/readiness
```

## Kubernetes

The following example keeps non-secret runtime configuration in a ConfigMap.
If your identity provider needs credentials for another extension, put those
values in a Kubernetes Secret and reference it with `secretKeyRef` instead.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: material-edc-config
data:
  EDC_PARTICIPANT_ID: material-edc
  WEB_HTTP_PORT: "8181"
  WEB_HTTP_PATH: /api
  WEB_HTTP_MANAGEMENT_PATH: /management
  WEB_HTTP_PROTOCOL_PATH: /protocol
  DATAPLANE_ID: material-edc-http-pull
  DATAPLANE_AUTHORIZATION: oauth2_client_credentials
  DATAPLANE_AUTHORIZATION_JWKS_URI: https://identity.example/.well-known/jwks.json
  EDC_IAM_OAUTH2_ISSUER: https://identity.example/
  EDC_IAM_OAUTH2_JWKS_URL: https://identity.example/.well-known/jwks.json
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: material-edc
spec:
  replicas: 1
  selector:
    matchLabels:
      app: material-edc
  template:
    metadata:
      labels:
        app: material-edc
    spec:
      containers:
        - name: material-edc
          image: your-registry/material-edc:latest
          imagePullPolicy: IfNotPresent
          envFrom:
            - configMapRef:
                name: material-edc-config
          ports:
            - name: http
              containerPort: 8181
          readinessProbe:
            httpGet:
              path: /api/readiness
              port: http
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /api/liveness
              port: http
            initialDelaySeconds: 40
            periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: material-edc
spec:
  selector:
    app: material-edc
  ports:
    - name: http
      port: 8181
      targetPort: http
```

Apply it after replacing the image reference:

```bash
kubectl apply -f material-edc-kubernetes.yaml
```

For production, use a fixed image digest instead of `:latest`, configure TLS
at the ingress or connector, and ensure the public DSP callback address is
reachable by other participants.
