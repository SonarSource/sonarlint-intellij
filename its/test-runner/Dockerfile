FROM ubuntu:22.04

VOLUME /home/dev/user_plugins
VOLUME /home/dev/test_resources

USER root
ENV HOME /home/dev
ENV USER root
ENV DISPLAY :1
WORKDIR /opt/idea

# should be one of: IC, CL, GO
ARG flavor
ARG version

RUN apt-get update && apt-get install -y wget unzip xvfb fluxbox x11vnc \
    && apt-get clean && rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*

# install IDE
RUN wget https://download.jetbrains.com/idea/idea${flavor}-${version}.tar.gz -O archive.tgz \
    && tar --strip-components=1 -xzf archive.tgz && rm archive.tgz

# install robot plugin
RUN wget https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/com/intellij/remoterobot/robot-server-plugin/0.11.9/robot-server-plugin-0.11.9.zip -O robot-plugin.zip \
    && unzip robot-plugin.zip -d plugins/ && rm robot-plugin.zip

# robot-server
EXPOSE 8082
# vncserver
EXPOSE 5900

# this folder needs to exist to bypass the first-time wizard
RUN mkdir -p /home/dev/config
COPY --chmod=755 files/xvfb /etc/init.d/
COPY --chmod=755 files/run.sh .
COPY files/idea.properties $HOME/
COPY files/idea.vmoptions .

# not ideal, should set only one
ENV IDEA_VM_OPTIONS /opt/idea/idea.vmoptions
ENV GOLAND_VM_OPTIONS /opt/idea/idea.vmoptions
ENV CLION_VM_OPTIONS /opt/idea/idea.vmoptions

ENTRYPOINT ["/bin/bash"]
CMD ["./run.sh"]