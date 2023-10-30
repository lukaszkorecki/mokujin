# Mokujin

> Mokujin (木人 Wooden person?) is a character in the Tekken series of fighting games. It is a spiritually sensitive animated training dummy, and serves as a guardian of good against supernatural evil threats.

## Logs

```clojure

(require '[mokujin.log :as log])

(log/info "test test")
(log/warn {:foo "bar"} "test test with context")

```


### Configuration

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <fieldNames>
      <!-- rename @timestamp to timestamp -->
        <timestamp>timestamp</timestamp>
        <!-- drop @version field, we don't need it -->
        <version>[ignore]</version>
      </fieldNames>
    </encoder>
  </appender>

<!-- whatever ignores you need -->
  <logger name="lockjaw.core" level="WARN" />
  <logger name="taskmaster" level="ERROR" />
  <logger name="athrun" level="ERROR" />
  <logger name="utility-belt.sql.component.connection-pool" level="OFF" />
  <logger name="com.zaxxer.hikari.HikariDataSource" level="OFF" />


  <root level="info">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>

```


## Errors

- [ ] TODO
