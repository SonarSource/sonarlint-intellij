ARG CIRRUS_AWS_ACCOUNT=275878209202
ARG JDK_VERSION=17
FROM ${CIRRUS_AWS_ACCOUNT}.dkr.ecr.eu-central-1.amazonaws.com/base:j${JDK_VERSION}-g7-latest

USER root

ENV NODE_VERSION=18

# dbus-x11 is for SonarLint token secure storage (required by the IDE)
RUN apt-get update && apt-get install -y metacity xvfb xauth ffmpeg \
    dbus-x11 \
    nodejs=${NODE_VERSION}.* \
    build-essential \
    gettext-base \
    jq

COPY --chmod=755 .cirrus/xvfb /etc/init.d/

USER sonarsource
