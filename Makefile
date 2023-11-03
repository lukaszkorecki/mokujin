
.PHONY: test-all


test-all:
	clj -M:dev:test
	cd examples/logback && clj -M:run
	cd examples/log4j2 && clj -M:run
