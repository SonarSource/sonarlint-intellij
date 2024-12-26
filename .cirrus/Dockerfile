ARG CIRRUS_AWS_ACCOUNT=275878209202
ARG JDK_VERSION=17
FROM ${CIRRUS_AWS_ACCOUNT}.dkr.ecr.eu-central-1.amazonaws.com/base:j${JDK_VERSION}-g7-latest

USER root

ENV NODE_VERSION=18

RUN wget https://download.java.net/java/GA/jdk17/0d483a095b4e4c4b9bde5b8b1e1b1e1b/17/GPL/openjdk-17_linux-x64_bin.tar.gz && \
    mkdir -p /opt/java/jdk17 && \
    tar -xzf openjdk-17_linux-x64_bin.tar.gz -C /opt/java/jdk17 --strip-components=1 && \
    rm openjdk-17_linux-x64_bin.tar.gz

ENV JAVA_HOME=/opt/java/jdk17

# Download and install JDK 17
RUN apt-get update && \
    apt-get install -y wget && \
    wget https://download.java.net/java/GA/jdk17/0d483a095b4e4c4b9bde5b8b1e1b1e1b/17/GPL/openjdk-17_linux-x64_bin.tar.gz && \
    mkdir -p /opt/java/openjdk && \
    tar -xzf openjdk-17_linux-x64_bin.tar.gz -C /opt/java/openjdk --strip-components=1 && \
    rm openjdk-17_linux-x64_bin.tar.gz

ENV JAVA_HOME=/opt/java/openjdk

RUN apt-get update && apt-get install -y metacity xvfb xauth ffmpeg \
    nodejs=${NODE_VERSION}.* \
    build-essential \
    gettext-base \
    jq \
    unzip
RUN rm -f dotnet.tar.gz && curl -o dotnet.tar.gz https://download.visualstudio.microsoft.com/download/pr/9454f7dc-b98e-4a64-a96d-4eb08c7b6e66/da76f9c6bc4276332b587b771243ae34/dotnet-sdk-8.0.101-linux-x64.tar.gz
RUN export DOTNET_ROOT=$(pwd)/.dotnet &&\
    mkdir -p "$DOTNET_ROOT" &&\
    tar zxf "dotnet.tar.gz" -C "$DOTNET_ROOT"

# Download and extract IntelliJ sources
RUN curl -L -o intellij.zip https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2022.3.1/ideaIC-2022.3.1.zip && \
    mkdir -p /opt/intellij && \
    curl -L -o /opt/intellij/ideaIC-2022.3.1.pom https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2022.3.1/ideaIC-2022.3.1.pom && \
    unzip intellij.zip -d /opt/intellij && \
    chown sonarsource: /opt/intellij && \
    chmod -R 755 /opt/intellij && \
    rm intellij.zip && \
    ls /opt/intellij

# Download and extract Rider sources
RUN curl -L -o resharper.zip https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/rider/riderRD/2024.1/riderRD-2024.1.zip && \
    mkdir -p /opt/resharper && \
    curl -L -o /opt/resharper/riderRD-2024.1.pom https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/rider/riderRD/2024.1/riderRD-2024.1.pom && \
    unzip resharper.zip -d /opt/resharper && \
    chown sonarsource: /opt/resharper && \
    chmod -R 755 /opt/resharper && \
    rm resharper.zip && \
    ls /opt/resharper

# Download and extract Resharper sources
RUN curl -L -o rider.zip https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/rider/riderRD/2022.3.1/riderRD-2022.3.1.zip && \
    mkdir -p /opt/rider && \
    curl -L -o /opt/rider/riderRD-2022.3.1.pom https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/rider/riderRD/2022.3.1/riderRD-2022.3.1.pom && \
    unzip rider.zip -d /opt/rider && \
    chown sonarsource: /opt/rider && \
    chmod -R 755 /opt/rider && \
    rm rider.zip && \
    ls /opt/rider

# Download and extract CLion sources
RUN curl -L -o clion.zip https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/clion/clion/2022.3.1/clion-2022.3.1.zip && \
    mkdir -p /opt/clion && \
    curl -L -o /opt/clion/clion-2022.3.1.pom https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/clion/clion/2022.3.1/clion-2022.3.1.pom && \
    unzip clion.zip -d /opt/clion && \
    chown sonarsource: /opt/clion && \
    chmod -R 755 /opt/clion && \
    rm clion.zip && \
    ls /opt/clion

# Download and extract Ultimate sources
RUN curl -L -o ultimate.zip https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2022.3.1/ideaIU-2022.3.1.zip && \
    mkdir -p /opt/ultimate && \
    curl -L -o /opt/ultimate/ideaIU-2022.3.1.pom https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2022.3.1/ideaIU-2022.3.1.pom && \
    unzip ultimate.zip -d /opt/ultimate && \
    chown sonarsource: /opt/ultimate && \
    chmod -R 755 /opt/ultimate && \
    rm ultimate.zip && \
    ls /opt/ultimate

COPY --chmod=755 .cirrus/xvfb /etc/init.d/

USER sonarsource
