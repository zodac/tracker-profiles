# syntax=docker/dockerfile:1.7
# tracker-profiles' layer of the sandbox image: the toolchains it needs on top of the shared base
# (Debian 13, nested Docker, Node, git, gh, jq, python3, lsof, iproute2, tzdata - see
# ai-sandbox/image/base/Dockerfile, whose pins belong to that repo). Claude Code and the sandbox
# scripts are added ABOVE this layer, so a Claude release never rebuilds anything here. Built by
# ai-sandbox/sandbox.sh with this directory as the build context.
#
# Adds JDK 27, Maven, and Chromium with its OS libraries for headless Selenium testing. The pins
# are maintained by .github/scripts/update_dependency_versions.sh, which matches the two `AS jdk`
# / `AS maven` stage lines below and the BEGIN/END DEBIAN PACKAGES block, so keep their shape.
#
# Layer order: the toolchain COPYs first (rarely move, and a change re-copies everything below),
# then the ENV block, and the most volatile apt pins last.

# The base image to build on - always passed by sandbox.sh.
ARG BASE_IMAGE

# ── Toolchain source stages (their payload is copied into the shared base) ────
# Each is only mined for its self-contained toolchain directory; none of their base OS reaches the image.
#   JDK   - self-contained under /opt/java/openjdk, portable onto glibc Debian.
#   Maven - pure Java + shell under /usr/share/maven; the image's own JDK is unused.
FROM eclipse-temurin:27_35-jdk AS jdk
FROM maven:3.10.0-eclipse-temurin-27 AS maven

# ── Runtime: the shared base + this project's toolchains ──────────────────────
FROM ${BASE_IMAGE}
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

COPY --from=jdk   /opt/java/openjdk /opt/java/openjdk
COPY --from=maven /usr/share/maven  /opt/maven

# fontconfig backs the copied-in Temurin JDK's reflective java.desktop paths (used by Selenium's
# AWT-dependent screenshot/image processing). chromium provides a headless browser for running
# the application in the sandbox without the production Docker image.
# hadolint ignore=DL3009
# BEGIN DEBIAN PACKAGES
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -yqq --no-install-recommends \
        chromium="156.0.8078.79-1~deb13u1" \
        fontconfig="2.15.0-2.3"
# END DEBIAN PACKAGES

ENV JAVA_HOME=/opt/java/openjdk \
    MAVEN_HOME=/opt/maven \
    PATH=/opt/java/openjdk/bin:/opt/maven/bin:${PATH}
