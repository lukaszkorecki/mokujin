# Mokujin

<img src="https://static.wikia.nocookie.net/topstrongest/images/1/15/Mokujin_TTT2.png/revision/latest/scale-to-width-down/1000?cb=20200503180655" align="right" height="250" />

> Mokujin (木人 Wooden person?) is a character in the Tekken series of fighting games. It is a spiritually sensitive animated training dummy, and serves as a guardian of good against supernatural evil threats.

> **Warning**
> While Mokujin has been used in live, production applications - it's still under *some* development, consider the API kinda-stable, with possible small changes

## Just enough (structured) logging

`clojure.tools.logging` is Pretty Good :tm: but it's missing Mapped Diagnostic Context (MDC) support - a way of adding **structured** information to your logs.
Mokujin wraps `clojure.tools.logging` and inject SLF4J's MDC into logging statements, if provided. It tries to be as efficient as possible, perserves the callers
context (line numbers etc) and minimizes any overhead beyond managing the MDC.


Rather than logging something like this:

```clojure
(require '[clojure.tools.logging :as log])

(defn do-signup [req]
  (let [user (create-user .... )]
    (if (:id user)
      (log/infof "signup user_id=%s email=%s" (:id user) (:email user))
      (log/error "signup failed email=%s" (:email (:body req))))))

```

You are (probably) be better off doing something like this, and structure your logs to avoid your log collector parsing them using regex patterns:


```clojure
(defn create-user [{:keys [email]}]
  {:id "foo" :email email})

(require '[mokujin.log :as log])

(defn do-sign-up [req]
  (log/with-context {:flow "signup" :email (:email (:body req))}
    (let [user (create-user req)]
      (if (:id user)
        (log/info {:id (:id user)} "success")
        (log/error "failed")))))

(do-sign-up {:body  {:email "test@example.com"}})
```

It would produce the following, in plain text and JSON:

```
2023-11-03 17:53:25,850 [app.server] [user] [INFO] success flow=signup, email=test@example.com, id=foo
```

```json
{
  "timestamp": "2023-11-03T17:53:25.850957Z",
  "message": "success",
  "logger_name": "user",
  "thread_name": "app.server",
  "level": "INFO",
  "level_value": 20000,
  "flow": "signup",
  "email": "test@example.com",
  "id": "foo"
}


```
> *Note*
> Output depends on the logging backend and appender configuration


Furthermore, you can add/write a Ring middleware to inject request data to the logging context and make all your logging statements simpler.

In my own projects, I'm using Mokujin with Logback and Logback's Logstash appender for producing JSON logs that get sent to a JSON-aware log collector.
Technically, anything that supports SLF4j and  MDC should work with some configuration. See `examples` directory and Getting started section.


## API & Usage

The API is close enough that Mokujin is *almost* a drop-in replacement for `c.t.logging`, **however** to force good practices,
it **does not include** logging functions that support format strings e.g. `log/infof` or `log/errorf`.
That's because in 99% of the cases where I'd use `log/infof` what I wanted to do was `(log/info context "message")` instead.
In cases where you really really want to use formatted strings, this Works Just Fine :tm: :
```
(log/info (format "thing %s happened to %s" thing-a thing-b))
```


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

> **Note**
> While I'm finalizing Mokujin's internals, there's no Maven (Clojars) available yet, use `git` dependency for now

```
 io.github.lukaszkorecki/mokujin {:git/sha "....."}
```

But wait, **you're not done yet**! You need to include a logging backend which suports MDC. For Logback, this would be:


```clojure
{io.github.lukaszkorecki/mokujin {:git/sha "...." :git/tag ".... "}
 org.slf4j/jcl-over-slf4j {:mvn/version "2.0.9"}
 ch.qos.logback/logback-classic {:mvn/version "1.4.11"
                                 :exclusions [org.slf4j/slf4j-api]}
 ;; for JSON output, you can use:
 net.logstash.logback/logstash-logback-encoder {:mvn/version "7.4"}}
```

And you're *almost ready to go*, if you're using Logback, you need to drop some configuration:


### Sample Logback configuration

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
  <logger name="utility-belt.sql" level="OFF" />
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
- [ ] Finalize `log/error` API - it works, but there are cases where its usage can be confusing
