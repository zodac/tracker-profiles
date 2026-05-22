# Stage 1: Patch chromedriver
FROM python:3.14.5-slim-trixie AS chromedriver_builder

# Install required applications using 'tracker-profiles/.github/scripts/update_dependency_versions.sh'
# - ca-certificates (SSL)
# - chromium (web browser)
# - curl (download chromedriver)
# - unzip (unzip chromedriver)
# BEGIN DEBIAN PACKAGES
RUN apt-get update && \
    apt-get install -yqq --no-install-recommends \
        ca-certificates="20250419" \
        chromium="148.0.7778.178-1~deb13u1" \
        curl="8.14.1-2+deb13u3" \
        unzip="6.0-29" \
    && \
    apt-get autoremove && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
# END DEBIAN PACKAGES

# Copy Python requirements
WORKDIR /app
COPY ./docker/config/requirements.txt .
RUN python3 -m pip install --no-cache-dir --upgrade pip && python3 -m pip install --no-cache-dir -r ./requirements.txt

# Download latest chromedriver for chromium verison then patch with undetected-chromedriver
COPY ./docker/scripts/patch_chromedriver.py /usr/local/bin/patch_chromedriver.py
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN mkdir -p /usr/local/chromium && \
    curl -fL \
        "https://storage.googleapis.com/chrome-for-testing-public/$(curl -s "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_$(chromium --version | awk '{print $2}' | cut -d. -f1)")/linux64/chromedriver-linux64.zip" \
        -o /tmp/chromedriver.zip && \
    unzip /tmp/chromedriver.zip -d /usr/local/chromium && rm -f /tmp/chromedriver.zip && \
    chmod -R 0755 /usr/local/chromium && \
    chmod +x /usr/local/bin/patch_chromedriver.py && \
    python3 /usr/local/bin/patch_chromedriver.py

# Stage 2: Create minimal JDK
FROM eclipse-temurin:26_35-jdk AS jdk_builder

# Hardcoding the required modules instead of using $(jdeps --multi-release BASE --print-module-deps --ignore-missing-deps /tmp/tracker-profiles.jar)
# Dependencies are unlikely to change, so this extra step is unnecessary for every build
#COPY --from=maven_builder /app/tracker-profiles-screenshots/target/tracker-profiles-screenshots-*.jar /tmp/tracker-profiles.jar
# BEGIN UBUNTU PACKAGES
RUN apt-get update && \
    apt-get install -yqq --no-install-recommends \
        binutils="2.42-4ubuntu2.10" \
    && \
    apt-get autoremove && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
# END UBUNTU PACKAGES
RUN jlink --compress=zip-9 \
        --no-header-files \
        --no-man-pages \
        --strip-debug \
        --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.rmi,java.scripting,java.sql,jdk.unsupported \
        --output "/opt/jdk" && \
    strip -p --strip-unneeded "/opt/jdk/lib/server/libjvm.so" && \
    { find /opt/jdk/bin -type f -exec strip -p --strip-unneeded {} \; || true; }

# Stage 3: Build the Java artefact
FROM maven:3.9.16 AS maven_base
FROM eclipse-temurin:26_35-jdk AS java_app_builder

# Extract Maven binary and libraries from official Maven image
COPY --from=maven_base /usr/share/maven /usr/share/maven
ENV M2_HOME="/usr/share/maven"
ENV MAVEN_HOME="/usr/share/maven"
ENV PATH="${M2_HOME}/bin:${PATH}"

# Set the working directory
WORKDIR /app

# Copy the pom.xml files and build dependencies (to cache them)
COPY pom.xml .
COPY tracker-profiles-screenshots/pom.xml ./tracker-profiles-screenshots/pom.xml
RUN mvn dependency:go-offline

# Copy source code and build the project, with dependencies cached
COPY tracker-profiles-screenshots/src ./tracker-profiles-screenshots/src
RUN mvn clean install

# Stage 4: Generate static AppCDS archive to reduce startup time and memory footprint
FROM eclipse-temurin:26_35-jdk AS appcds_builder
WORKDIR /app

COPY --from=jdk_builder /opt/jdk /opt/jdk
ENV JAVA_HOME="/opt/jdk"
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=java_app_builder /app/tracker-profiles-screenshots/target/tracker-profiles-screenshots-*.jar /app/tracker-profiles.jar

# One unrecognised tracker name triggers TrackerHandlerFactory's reflection scan, which loads all
# handler classes via Class.forName(), then the app exits cleanly with "no trackers selected"
RUN printf 'trackerName,username,password\nAppCdsTraining,user,pass\n' > /tmp/trackers.csv

# Step 1: Collect the list of all classes loaded during a representative run
# -Xshare:off disables any existing archive so the class list reflects actual class-loading order
# java.awt.headless=true returns a HeadlessGraphicsEnvironment without connecting to an X11 display
# DISPLAY=:0 satisfies ApplicationLauncher's non-null DISPLAY env var check
# FAIL_ON_UNSUPPORTED_TRACKER=false allows the training-only CSV row to be skipped without error
# The command exits with code 1 ("no trackers selected"); the class list is still written on exit
RUN DISPLAY=":0" \
    TRACKER_INPUT_FILE_PATH=/tmp/trackers.csv \
    FAIL_ON_UNSUPPORTED_TRACKER=false \
    java \
        -Djava.awt.headless=true \
        -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders \
        -Xshare:off \
        -XX:DumpLoadedClassList=/app/classes.lst \
        -jar /app/tracker-profiles.jar; \
    [ -f /app/classes.lst ] || touch /app/classes.lst

# Step 1b: Remove Log4j2 async classes from the class list
# AsyncLoggerContext and its siblings in the log4j/core/async package reference com.lmax.disruptor, which is an optional
# Log4j2 dependency only needed for async logging.
# Since async logging is not configured here, Disruptor is not on the classpath. Leaving these classes in causes CDS to
# fail its bytecode verification and.
RUN sed -i '/log4j.*[Aa]sync/d' /app/classes.lst

# Step 2: Dump the static AppCDS archive from the collected class list
# Classes that cannot be resolved or fail to initialise are silently excluded from the archive
# The same env vars as step 1 ensure static initialisers that call Configuration.get() succeed
# The command exits with code 1 (Xshare:dump exits after creating the archive, before main runs); the fallback touch
# ensures a file always exists so the COPY in the runtime stage never fails \
# At runtime, -Xshare:auto silently ignores an empty or incompatible archive
RUN DISPLAY=":0" \
    TRACKER_INPUT_FILE_PATH=/tmp/trackers.csv \
    FAIL_ON_UNSUPPORTED_TRACKER=false \
    java \
        -Djava.awt.headless=true \
        -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders \
        -Xshare:dump \
        -XX:SharedClassListFile=/app/classes.lst \
        -XX:SharedArchiveFile=/app/app.jsa \
        -jar /app/tracker-profiles.jar 2>/dev/null; \
    [ -f /app/app.jsa ] || touch /app/app.jsa

# Runtime
FROM debian:13.5-slim AS runtime
# Set the working directory
WORKDIR /app

# Metadata
ARG BUILD_DATE="unknown"
ARG GIT_REVISION="unknown"
ARG VERSION="unknown"
LABEL org.opencontainers.image.authors="zodac <arouge110@msn.com>" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.description="Docker-based tool used to log in to private trackers and take a screenshot of the user's profile page, including the option to hide sensitive information" \
      org.opencontainers.image.documentation="https://github.com/zodac-personal/tracker-profiles/blob/master/README.md" \
      org.opencontainers.image.licenses="0BSD" \
      org.opencontainers.image.revision="${GIT_REVISION}" \
      org.opencontainers.image.source="https://github.com/zodac-personal/tracker-profiles" \
      org.opencontainers.image.title="tracker-profiles" \
      org.opencontainers.image.url="https://hub.docker.com/repository/docker/zodac/tracker-profiles/general" \
      org.opencontainers.image.version="${VERSION}"

# Suppress 'dbind-WARNING' error logs
ENV NO_AT_BRIDGE=1

# Install required applications using 'tracker-profiles/.github/scripts/update_dependency_versions.sh'
# - ca-certificates (SSL)
# - chromium (web browser)
# - fonts-arphic-ukai (Chinese fonts)
# - fonts-ipafont (Japanese fonts)
# - wmctrl (force browser to be active window)
# BEGIN DEBIAN PACKAGES
RUN apt-get update && \
    apt-get install -yqq --no-install-recommends \
        ca-certificates="20250419" \
        chromium="148.0.7778.178-1~deb13u1" \
        fonts-arphic-ukai="0.2.20080216.2-5" \
        fonts-ipafont="00303-23" \
        wmctrl="1.07+git20240228.1105759-1" \
    && \
    apt-get autoremove && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
# END DEBIAN PACKAGES

# Copy Google Chrome extensions
COPY ./docker/browser_extensions/ublock_origin_lite_ddkjiahejlhfcafbddmgiahcphecmpfh_2026_2_22.crx /app/ublock_origin_lite.crx

# Copy patched chromedriver
COPY --from=chromedriver_builder /usr/local/chromium/chromedriver-linux64/chromedriver /usr/local/chromium/chromedriver-linux64/chromedriver

# Copy scripts
COPY --chmod=755 ./docker/scripts/start.sh /app/start.sh

# Configure nonroot user
ARG PUID=1000
ARG PGID=1000
# Create user and group
RUN groupadd -g ${PGID} nonroot && \
    useradd -l -u ${PUID} -g nonroot -d /tmp/chrome-home -m nonroot && \
    chown -R nonroot:nonroot /tmp/chrome-home
# Set environment and user
ENV HOME=/tmp/chrome-home
USER nonroot
VOLUME /tmp/chrome-home

# Copy JDK & JAR
COPY --from=jdk_builder /opt/jdk /opt/jdk
ENV JAVA_HOME="/opt/jdk"
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=java_app_builder /app/tracker-profiles-screenshots/target/tracker-profiles-screenshots-*.jar tracker-profiles.jar
COPY --from=appcds_builder /app/app.jsa app.jsa
# Copy JUL config so start.sh can pass it via -Djava.util.logging.config.file
# JUL re-reads this file after every LogManager.reset(), which Selenium triggers on each RemoteWebDriver creation
COPY --from=java_app_builder /app/tracker-profiles-screenshots/src/main/resources/logging.properties logging.properties

# Start application
CMD ["/app/start.sh"]
# Debugging
#ENTRYPOINT ["tail", "-f", "/dev/null"]
