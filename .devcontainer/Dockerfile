# Use Eclipse Temurin JDK 21
FROM eclipse-temurin:21-jdk

# Install basic utilities
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    unzip \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 24.12.0 LTS
ENV NODE_VERSION=24.12.0
RUN curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

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

# Install OpenSpec CLI globally
RUN npm install -g @fission-ai/openspec@latest

# Default command
CMD ["/bin/bash"]

