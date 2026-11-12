# externs-phpparser — performance checks

## Workload and method

Run the tagged microbenchmarks with `./gradlew performanceTest --rerun`. The default test task excludes them. Use independent JVM invocations and report their spread before attributing a difference to an implementation choice.

Measure archive extraction, version matching, executable lookup and command construction separately from end-to-end parsing. A digest microbenchmark measures digest computation; it does not establish its share of a parse invocation.

## Resource constraints

The parser runs in an external process. End-to-end measurements include process startup and output I/O. The process timeout, cache identity and publication behavior are specified in [design](design.md); the JSON writer’s growth bounds are in [spec](spec.md#complexity).
