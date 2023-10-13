# UI test-runner

Docker image containing IntelliJ UI tests infrastructure. The image contains:

* the target IDE (flavor + version). Defined with an argument at build-time
* the robot-server allowing to interact with the UI
* utilities to expose a VNC server to enable capturing a video from the outside

The robot-server will listen on 8082.
The VNC server will listen on 5900 (view only).

The image defines 2 volumes, that let users provide:
* the plugin to test. Has to be unzipped first, and the containing folder must be mounted on /home/dev/user_plugins (read-only)
* test resources. The user provided folder will be mounted on /home/dev/test_resources

# Build the image

> docker build --build-arg flavor=IC --build-arg version=2021.3 -t idea-ic:2021.3 .

# Start a container from the image

> docker run --name ic-2021 -p 8082:8082 -p5900:5900 -v /path/to/unzipped/plugin:/home/dev/user_plugins:ro -v /path/to/test/resources:/home/dev/test_resources idea-ic:2021.3

TODO

> properly stop on Ctrl-C
> make SQ available from the container
> modify JUnit runner: https://github.com/StefanHufschmidt/TestcontainersJUnit5ParallelExample/tree/main/src/test/java/com/example
> check how to integrate JaCoCo