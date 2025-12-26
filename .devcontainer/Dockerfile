# Use Eclipse Temurin JDK 25
FROM eclipse-temurin:25-jdk

# Install basic utilities
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle 9.2.1 (matches project's wrapper version)
# This allows us to use 'gradle wrapper' to properly set up the wrapper
ENV GRADLE_VERSION=9.2.1
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${GRADLE_HOME}/bin:${PATH}"

RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    mv /opt/gradle-${GRADLE_VERSION} ${GRADLE_HOME} && \
    rm /tmp/gradle.zip && \
    chmod -R 755 ${GRADLE_HOME}

# Default command
CMD ["/bin/bash"]

