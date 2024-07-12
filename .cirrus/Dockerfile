ARG CIRRUS_AWS_ACCOUNT=275878209202
ARG JDK_VERSION=17
FROM ${CIRRUS_AWS_ACCOUNT}.dkr.ecr.eu-central-1.amazonaws.com/base:j${JDK_VERSION}-g7-latest

USER root

ENV NODE_VERSION=18
RUN apt-get update && apt-get install -y metacity xvfb xauth ffmpeg \
    nodejs=${NODE_VERSION}.* \
    build-essential \
    gettext-base \
    jq
RUN rm -f dotnet.tar.gz && curl -o dotnet.tar.gz https://download.visualstudio.microsoft.com/download/pr/9454f7dc-b98e-4a64-a96d-4eb08c7b6e66/da76f9c6bc4276332b587b771243ae34/dotnet-sdk-8.0.101-linux-x64.tar.gz
RUN export DOTNET_ROOT=$(pwd)/.dotnet &&\
    mkdir -p "$DOTNET_ROOT" &&\
    tar zxf "dotnet.tar.gz" -C "$DOTNET_ROOT"

COPY --chmod=755 .cirrus/xvfb /etc/init.d/

USER sonarsource
