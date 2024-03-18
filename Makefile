.PHONY: test test-all test-logback test-log4j2 update-deps benchmark release clean jar publish


test-all: test test-logback test-log4j2
	@echo "all done"

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


ifneq ($(SNAPSHOT),)
snapshot := :snapshot $(SNAPSHOT)
endif

clean:
	clj -T:build clean

jar:
	clj -T:build jar  $(snapshot)


publish:
	clj -T:build publish $(snapshot)

release: clean jar publish
