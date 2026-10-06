# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk AS build

WORKDIR /workspace

# Copy the complete multi-module build. The build context must be EDC-Connector.
COPY . .

RUN chmod +x ./gradlew \
    && ./gradlew :material-edc:installDist --no-daemon

FROM eclipse-temurin:26-jre

ENV EDC_PARTICIPANT_ID=material-edc \
    WEB_HTTP_PORT=8181 \
    WEB_HTTP_PATH=/api \
    WEB_HTTP_MANAGEMENT_PATH=/management \
    WEB_HTTP_PROTOCOL_PATH=/protocol \
    DATAPLANE_ID=material-edc-http-pull \
    DATAPLANE_AUTHORIZATION=oauth2_client_credentials

WORKDIR /opt/material-edc

COPY --from=build /workspace/material-edc/build/install/material-edc/ ./

EXPOSE 8181

ENTRYPOINT ["/opt/material-edc/bin/material-edc"]
