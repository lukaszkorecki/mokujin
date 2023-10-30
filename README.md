# Mokujin

> Mokujin (木人 Wooden person?) is a character in the Tekken series of fighting games. It is a spiritually sensitive animated training dummy, and serves as a guardian of good against supernatural evil threats.

## Just enough logging

`clojure.tools.logging` is Pretty Good :tm: but it's missing Mapped Diagnostic Context (MDC) support. What's that?

Rather than logging something like this:

```clojure
(require '[clojure.tools.logging :as log])

(defn do-signup [req]
  (let [user (create-user .... )]
    (if (:id user)
      (log/infof "signup user_id=%s email=%s" (:id user) (:email user))
      (log/error "signup failed email=%s" (:email (:body req))))))

```

You probably would be better off doing something like this, and structure your logs:


```clojure
(require '[mokujin.log :as log])

(defn do-singup [req]
  (log/with-context {:flow "signup" :email (:email (:body req))}
    (let [user (create-user ...)]
      (if (:id user)
        (log/info {:id (:id user)} "success")
        (log/error "failed")))))


```

Mokujin wraps `clojure.tools.logging` and inject SLF4J's MDC into it. It's efficient as possible, and doesn't introduce any overhead beyond managing the MDC.

By default Mokujin ships with Logback and Logback's Logstash appender for producing JSON logs. You can exclude them and use a different logging backend such as Log4j2.


## API & Usage

The API is close enough that Mokujin is *almost* a drop-in replacement for `c.t.logging`, **however** to force good practices, it **does not include** logging functions that support format strings.
That's because in 99% of the cases where I'd use `log/infof` what I wanted to do was `(log/info context "message")` instead.


If you really have to, you can do something like this `(log/warn (format "message %s" arg))`


Supported logging functions:

```clojure
(log/info [msg] [ctx msg])
(log/warn [msg] [ctx msg])
(log/debug [msg] [ctx msg])
(log/error [msg] [exc msg] [ctx exc msg])
```

> **Note**
> `log/error` doesn't support the context/MDC argument for 1- and 2-arity variants, again - that's because most of the time you want to use within `log/with-context` call.

You can use `with-context` macro, to set context for the whole block. Contexts can be nested. All map keys and values will be stringified using `name` + `str` combo.

```clojure
(log/with-context ctx)
```

# Getting started


To get started, add Mokujin to your `deps.edn`

> No Maven releases available yet!


```
 io.github.lukaszkorecki/mokujin {:git/sha "....."}
```

And you're *almost ready to go*, if you're using Logback, you need to drop some configuration:


### Configuration

Here's my Logback configuration that I use in all production projects, with logs shipped to something that understands JSON/structured logging (Loki, Better Stack's Logs etc)


Production config, stored in `resources/logback.xml`:


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
  <logger name="taskmaster.async" level="ERROR" />
  <logger name="athrun" level="ERROR" />
  <logger name="utility-belt.sql.component.connection-pool" level="OFF" />
  <logger name="com.zaxxer.hikari.HikariDataSource" level="OFF" />


  <root level="info">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>

```

Development/test config, stored in `dev-resources/logback-test.xml`:


```xml
<configuration scan="true" scanPeriod="10 seconds">
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%date [%thread] [%logger] [%level] %msg %mdc%n</pattern>
    </encoder>
  </appender>
  <root level="info">
    <appender-ref ref="STDOUT" />
  </root>

</configuration>
```

## TODO

- [ ] Maven release?
- [ ] Finalize `log/error` API
