#!/bin/bash

export JAVA_OPTS="-javaagent:${HOME}/.m2/repository/io/opentelemetry/javaagent/opentelemetry-javaagent/2.21.0/opentelemetry-javaagent-2.21.0.jar"
export OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_LOGGER_CONTEXT_ATTRIBUTES=true
export OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_MDC_ATTRIBUTES=*
export OTEL_COLLECTOR_HOST=localhost
export OTEL_EXPORTER_OTLP_INSECURE=true
export OTEL_LOGS_EXPORTER=otlp
export OTEL_PROPAGATORS=tracecontext,baggage

clj -M:run
