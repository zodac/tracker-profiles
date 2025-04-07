# Stage 1: Build the application
FROM maven:3.9.9-eclipse-temurin-23-alpine AS maven_build

# Set the working directory
WORKDIR /app

# Copy the parent pom.xml and the child module directory
COPY pom.xml .
COPY tracker-profiles-screenshots/pom.xml ./tracker-profiles-screenshots/pom.xml
COPY tracker-profiles-screenshots/src ./tracker-profiles-screenshots/src

# Build the project
RUN mvn clean install

# Stage 2: Run the application
FROM openjdk:23-jdk-slim

# Set the working directory
WORKDIR /app

# Install Chrome
# Add Google Chrome repository key and source list
ARG CHROME_VERSION="134.0.6998.88-1"
RUN apt-get update && \
        apt-get install -yqq --no-install-recommends \
          gpg \
          wget \
          libxtst6 libxrender1 libxi6 \
          x11-apps \
        && \
    wget -qO - https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/google-chrome-keyring.gpg] http://dl.google.com/linux/chrome/deb/ stable main" | tee /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && \
    apt-get install -yqq --no-install-recommends \
      google-chrome-stable=${CHROME_VERSION} \
    && \
    apt-get autoremove && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy the compiled JAR file from the build stage
COPY --from=maven_build /app/tracker-profiles-screenshots/target/tracker-profiles-screenshots-*.jar tracker-profiles.jar

## Command to run the main function
COPY ./docker/start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]
