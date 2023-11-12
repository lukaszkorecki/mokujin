
.PHONY: test test-all test-logback test-log4j2


test-all: test test-logback test-log4j2
test:
	clj -M:dev:test

test-logback:
	cd examples/logback && clj -M:run

test-log4j2:
	cd examples/log4j2 && clj -M:run
