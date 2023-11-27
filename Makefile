.PHONY: test test-all test-logback test-log4j2 update-deps benchmark


test-all: test test-logback test-log4j2
test:
	clj -M:dev:test

test-logback:
	cd examples/logback && clj -M:run

test-log4j2:
	cd examples/log4j2 && clj -M:run

benchmark:
	clj -M:benchmark

update-deps:
	clj -M:dev/outdated
	cd examples/logback && clj -M:dev/outdated
	cd examples/log4j2 && clj -M:dev/outdated
