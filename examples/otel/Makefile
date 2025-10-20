prep:
	clj -X:deps prep

start-otel-stack:
	docker compose up otel

start-logger:
	JAVA_OPTS="-javaagent:$(HOME)/.m2/repository/io/opentelemetry/javaagent/opentelemetry-javaagent/2.21.0/opentelemetry-javaagent-2.21.0.jar -Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=* -Dotel.instrumentation.logback-appender.experimental.capture-logger-context-attributes=true" OTEL_COLLECTOR_HOST=localhost OTEL_EXPORTER_OTLP_INSECURE=true OTEL_LOGS_EXPORTER=otlp OTEL_PROPAGATORS=tracecontext,baggage clj -M:run
