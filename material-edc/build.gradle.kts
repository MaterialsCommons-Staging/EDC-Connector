/*
 *  Copyright (c) 2026 Materials Commons
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

plugins {
    `java-library`
    id("application")
}

dependencies {
    // Control plane, DSP, HTTP server and OAuth2 client support.
    implementation(project(":dist:bom:controlplane-base-bom"))

    // Data Plane Signaling and the HTTP pull dataplane implementation.
    implementation(project(":data-protocols:data-plane-signaling:data-plane-signaling-core"))
    implementation(project(":system-tests:e2e-transfer-test:signaling-data-plane"))

    // Centralised OAuth2 authentication for the Management API.
    implementation(project(":extensions:common:api:management-api-oauth2-authentication"))

    // The classic DSP stack still needs an IdentityService for protocol messages.
    // This is deliberately separate from the OAuth2 authentication of the APIs.
    implementation(project(":extensions:common:iam:iam-mock"))

    implementation(libs.jakarta.rsApi)
    runtimeOnly(libs.parsson)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

edcBuild {
    publish.set(false)
}
