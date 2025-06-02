# Stage 1: Build the application
FROM maven:3.9.9-eclipse-temurin-24-alpine AS maven_build

# Set the working directory
WORKDIR /app

# Copy the pom.xml files and build dependencies (to cache them)
COPY pom.xml .
COPY tracker-profiles-screenshots/pom.xml ./tracker-profiles-screenshots/pom.xml
RUN mvn dependency:go-offline

# Copy source code and build the project, with dependencies cached
COPY tracker-profiles-screenshots/src ./tracker-profiles-screenshots/src
RUN mvn clean install

# Stage 2: Install Python 3.11 with venv + pip
FROM python:3.11-slim AS python_build

# Ensure pip, venv, setuptools, and wheel are available
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc && \
    python3 -m ensurepip && \
    pip install --upgrade pip setuptools wheel && \
    apt-get purge -y gcc && \
    apt-get autoremove -y && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Stage 2: Run the application
FROM openjdk:24-jdk-slim

# Set the working directory
WORKDIR /app

# Configure non-root user
ENV PUID=1000
ENV PGID=1000

# Install required applications:
# - chromium (web browser)
# - fonts-arphic-ukai (font for Chinese language characters)
# - gosu (dynamic PGID/PUID to run application)
# - x11-apps (UI browser)
ARG CHROME_VERSION="137.0.7151.55-3~deb12u1"
RUN apt-get update && \
    apt-get install -yqq --no-install-recommends \
      chromium="${CHROME_VERSION}" \
      fonts-arphic-ukai \
      gosu \
      x11-apps \
    && \
    apt-get autoremove && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy Python installation from the python_build stage
COPY --from=python_build /usr/local /usr/local
ENV PATH="/usr/local/bin:${PATH}"

# Copy Python requirements
COPY ./docker/python/requirements.txt .
COPY ./docker/python/selenium_manager.py .

# Set up Python virtual environment and install dependencies
RUN python3 -m venv /app/venv && \
    /app/venv/bin/pip install --upgrade pip && \
    /app/venv/bin/pip install -r /app/requirements.txt && \
    chown -R "${PUID}":"${PGID}" /app/venv

# Copy the compiled JAR file from the build stage
COPY --from=maven_build /app/tracker-profiles-screenshots/target/tracker-profiles-screenshots-*.jar tracker-profiles.jar

# Command to run the main function
COPY ./docker/scripts/start.sh /app/start.sh
COPY ./docker/scripts/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/start.sh && chmod +x /app/entrypoint.sh

# Needed to avoid 'dbind-WARNING' error
ENV NO_AT_BRIDGE=1

# Use entrypoint for dynamic user creation
ENTRYPOINT ["/app/entrypoint.sh"]
# Start application
CMD ["/app/start.sh"]
