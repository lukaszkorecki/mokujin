.PHONY: test-core test-logback test-all test-ex-logback test-ex-log4j2 update-deps benchmark release clean jar publish


# Build & release tasks
SNAPSHOT ?= false
LIB = mokujin mokujin-logback

clean:
	$(foreach lib,$(LIB),clj -T:build clean :lib $(lib);)

jar:
	$(foreach lib,$(LIB),clj -T:build jar :lib $(lib) :snapshot $(SNAPSHOT);)


install:
	$(foreach lib,$(LIB),clj -T:build install :lib $(lib) :snapshot $(SNAPSHOT);)

publish: clean jar
	$(foreach lib,$(LIB),clj -T:build publish :lib $(lib) :snapshot $(SNAPSHOT);)

# Dev/test tasks


update-deps:
	clj -M:dev/outdated

test-all: test-core test-logback test-ex-logback test-ex-log4j2
	@echo "all done"

test-core:
	cd mokujin && clj -M:dev:test

test-logback:
	cd mokujin-logback & clj -M:dev:test

test-ex-logback:
	cd examples/logback && clj -M:run

test-ex-log4j2:
	cd examples/log4j2 && clj -M:run

benchmark:
	cd bench && clj -M:benchmark
