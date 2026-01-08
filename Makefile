.PHONY: test-core test-logback test-all test-ex-logback test-ex-log4j2 update-deps benchmark release clean jar publish


# Build & release tasks
SNAPSHOT ?= false
LIB = mokujin mokujin-logback

clean:
	$(foreach lib,$(LIB),clojure -T:build clean :lib $(lib);)

jar:
	$(foreach lib,$(LIB),clojure -T:build jar :lib $(lib) :snapshot $(SNAPSHOT);)


install:
	$(foreach lib,$(LIB),clojure -T:build install :lib $(lib) :snapshot $(SNAPSHOT);)

publish: clean jar
	$(foreach lib,$(LIB),clojure -T:build publish :lib $(lib) :snapshot $(SNAPSHOT);)

# Dev/test tasks


update-deps:
	$(foreach lib,$(LIB),cd $(lib) && clojure -M:dev/outdated && cd -;)

test-all: test-core test-logback test-ex-logback test-ex-log4j2
	@echo "all done"

test-core:
	cd mokujin && clojure -M:dev:test:run-tests

test-logback:
	cd mokujin-logback && clojure -M:dev:test:run-tests

test-ex-logback:
	cd examples/logback && clojure -M:run

test-ex-log4j2:
	cd examples/log4j2 && clojure -M:run

benchmark:
	cd bench && clojure -M:benchmark


help:
	@grep -E '^[a-z\-]+:' ./Makefile
