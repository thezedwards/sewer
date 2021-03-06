# Sewer - a high performance, reliable pixel server

Sewer is built for a single purpose: serving "204 No Content" responses via an embedded [Jetty](http://www.eclipse.org/jetty/) server and writing access logs to HDFS as quickly and reliably as possible.

Sewer was heavily inspired by [Apache Flume](https://cwiki.apache.org/FLUME/).


## Getting Started

1. Build or download the latest version:

        $ git clone https://github.com/chetan/sewer.git
        $ buildr test=no clean package
        $ cp target/sewer-*.tgz /opt

2. Unpack tarball:

        $ tar -xzf sewer-*.tgz

3. Configure Sink:

        $ vim conf/config.properties

4. Start

        $ bin/sewer.sh start

That's it! Sewer should now be up and running.

    # test the pixel server
    $ curl -v localhost:8080
    < HTTP/1.1 204 No Content

    # status is available on 8081 (e.g., for load balancer's that require a 200)
    $ curl -v localhost:8081
    < HTTP/1.1 200 OK
    < Content-Length: 0

    # jmx agent is on 7777
    $ jmx4perl http://localhost:7777/jolokia read org.eclipse.jetty.server.handler:id=0,type=statisticshandler requests
    1234

See [Jolokia](http://www.jolokia.org/) and [Jmx4Perl](https://metacpan.org/module/JMX::Jmx4Perl) for more about using the built-in JMX agent for monitoring and statistics gathering.

## Reliability

Sewer is designed to be extremely reliable for a number of different failure scenarios with minimal impact on performance.

It is designed to write directly to HDFS from the node which generates the event. As such, it is capable of surviving a *single downstream failure* and automatically retrying when the downstream issue has been resolved.

Types of errors that will be recovered from include:

* Network errors
* NameNode unreachable
* NameNode in safe-mode
* DateNode errors:
* HDFS create/close fails
* etc

### How it works

Events are written in batches which are rotated on a timer; e.g., every 30 seconds by default. When an event is received, it is first written to disk before attempting a write to HDFS. If a batch is successfully flushed and closed, the local buffer is deleted. On failure, the buffer remains and moves into a retry queue where it will be retried asynchronously until the downstream error is resolved and the batch closes cleanly.

### Stopping

When Sewer is stopped or receives a kill signal, it will try to cleanly shutdown. First the source is closed so no more events will be received. Then it tries to cleanly close down the current event batch. If there is a downstream failure, then any open batches will be drained automatically when Sewer is started again.

### Performance Tradeoffs

For maximum I/O performance, in-memory buffers are used in several locations. Thus, if the server were to suffer a hard crash (or a kill -9) it is possible that some events will be lost. This is considered to be an acceptable tradeoff as it would be impossible to guarantee zero event loss in such a case since at a minimum, there would be some number of active HTTP requests which would not complete. These lost connections would typically outnumber those lost due to internal buffering in any case.

## Log Format

Sewer is built on Hadoop's *Writable* data format. Access log events look like the following:

    long timestamp;
    String ip;
    String host;
    String requestPath;
    String queryString;
    String referer;
    String userAgent;
    String cookies;

It can be easily extended to write additional headers or handle other types of requests such as POST.

## Benchmarks

### EC2

* m1.small:   3,622 reqs/sec
* m1.large:  13,293 reqs/sec
* c1.medium: 16,556 reqs/sec
* m1.small via elb:  3,205 reqs/sec
* m1.large via elb:

Methodology: 2x m1.large load generators running 'ab' twice each with the following params:

    ab #{LONG_UA} -k -r -t 600 -n 500000 -c 400 #{URL}
    LONG_UA = 800 byte user agent header to simulate a large payload

Tests run January, 2012

## Not Quite a Flume Replacement

While Sewer uses the same source/sink pattern under the hood, it is not designed to be a drop-in Flume replacement. There is currently no master/server implementation for centrally controlling Sewer nodes, nor is there support for multiple flows or on-the-fly reconfiguration of nodes. Reconfiguration requires modifying the config file and bouncing the Sewer process.

That said, while Sewer was built for pixel serving, it should be relatively trivial to add more sources and sinks and build some of this extra functionality if so desired. In fact, there is already a basic IPC implementation modeled after Hadoop that is currently unused.

## License

Copyright 2012 Pixelcop Research, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
