.PHONY: test-core test-logback test-all test-ex-logback test-ex-log4j2 update-deps benchmark release clean jar publish


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
	clj -M:benchmark

update-deps:
	clj -M:dev/outdated
	cd examples/logback && clj -M:dev/outdated
	cd examples/log4j2 && clj -M:dev/outdated


ifneq ($(SNAPSHOT),)
snapshot := :snapshot $(SNAPSHOT)
endif

clean: clean-core clean-logback

clean-core:
	clj -T:build clean :lib-name mokujin

clean-logback:
	clj -T:build clean :lib-name mokujin-logback

jar: jar-core jar-logback

jar-core:
	clj -T:build jar  $(snapshot)


publish:
	clj -T:build publish $(snapshot)

release: clean jar publish
