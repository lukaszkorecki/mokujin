# Mokujin

<img src="https://static.wikia.nocookie.net/topstrongest/images/1/15/Mokujin_TTT2.png/revision/latest/scale-to-width-down/1000?cb=20200503180655" align="right" height="250" />

> Mokujin (木人 Wooden person?) is a character in the Tekken series of fighting games. It is a spiritually sensitive animated training dummy, and serves as a guardian of good against supernatural evil threats.

> **Warning**
> While Mokujin has been used in live, production applications - it's still under *active* development, consider the API stable-ish, with possible small changes here and there

## Just enough (structured) logging


### Rationalle

`clojure.tools.logging` is Good Enough :tm: but it would be great if it supported [Mapped Diagnostic Context (MDC)](https://logback.qos.ch/manual/mdc.html) - as
way of adding **structured** information to your logs. This way you get both structured data that can be used to dervive metrics, alerts etc in your log collector
and human readable logs for debugging pruposes.

Mokujin emerged after years of working with different logging solutions in Clojure (and other languages), log aggregation systems and monitoring platforms. It
strikes a balance between familiar API, and leveraging existing JVM ecosystem and all of its good (and bad) parts.

### How does it work?

Mokujin wraps `clojure.tools.logging` and inject SLF4J's MDC into logging statements, if provided. It tries to maintain a balance between new
features and the API of a widely used library. Mokujin wraps standard logging macros (`info`, `warn` etc) and adds
support for the context map argument, as the first arg.
`infof` (and similar) variants are present, but do not support MDC (more on that later).


#### Performance

> :warn: this part is still in progress

While effort was made to keep things as efficient as possible, there is some impact
introduced by manipulating the MDC object. Typical "raw" call to `clojure.tools.logging/info` is measured in nano-seconds.
Same call to `mokujin.log/info` will have nearly the same performance characteristics. Only when introducing several levels of MDC processing you can expect a slow down to micro-seconds.
Not bad, and for services that do a lot of I/O the impact is negligible. Additionally, your logging backend is more of a factor here.

You can run the benchmark via `clj -M:benchmark`.


### Show me the codes!

Rather than logging something like this (you probably shouldn't include emails in your logs, but that's a different topic):

```clojure
(require '[clojure.tools.logging :as log])

(defn do-signup [req]
  (let [user (create-user .... )]
    (if (:id user)
      (log/infof "signup user_id=%s email=%s" (:id user) (:email user))
      (log/errorf "signup failed email=%s" (:email (:body req))))))

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

It would produce the following:

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


From here, you can add/write a Ring middleware to inject request data to the logging context and make all your logging statements simpler.

In my own projects, I'm using Mokujin with Logback and Logback's Logstash appender for producing JSON logs that get sent to a JSON-aware log collector.
Very simple middlewares for Ring, Hato and the background processing system ensure that all logs are enriched by contextual data (uri's, request/response time etc).

## Logging backend support

Technically, anything that supports SLF4j and  MDC should work with some configuration. See `examples` directory and
Getting started section - Logback and log4j2 examples are provided.


## API & Usage

The API is close enough that Mokujin is *almost* a drop-in replacement for `c.t.logging`, **however** to force good practices,
logging functions that support format strings e.g. `log/infof` or `log/errorf` **do not suport the context map**.

That's because in 99% of the cases where I'd use `log/infof` what I wanted to do was `(log/info context "message")` instead.
In cases where you really really want to use formatted strings and the context, this Works Just Fine :tm: :

```clojure
(log/with-context {:some :ctx}
  (log/infof "thing %s happened to %s" thing-a thing-b))
```


### Full API

#### Logging statements

```clojure
(log/info [msg] [ctx msg])
(log/warn [msg] [ctx msg])
(log/debug [msg] [ctx msg])
(log/error [msg] [exc msg] [ctx exc msg])
(log/infof [fmt & fmt-args])
(log/warnf [fmt & fmt-args])
(log/debugf [fmt & fmt-args])
(log/errorf [fmt & fmt-args])
(log/with-context [ctx & body])
```

> **Note**
> `log/error` doesn't support the context/MDC argument for 1- and 2-arity variants, again - that's because most of the time you want to use within `log/with-context` call.
> Additionally, figuring out the types of passed in arguments gets complicated as we'd have to distinguish between  context map, exception, message string and rest of the args
> and that introduces overhead that we don't want.

Mokujin preserves caller context, and ensures that the right information is injected into the log statement, under the `logger` field.

#### Context macro

You can use `with-context` macro, to set context for a form, request handler body etc.
Contexts can be nested.

All map keys and values will be stringified using `name` + `str` combo.

```clojure
(log/with-context ctx body)
```

Example:

``` clojure
(defn handle-notification [user notification]
  (log/with-context {:user-id (:id user) :notification-type (:type notification)}
    (let [{:keys [success?] result} (do-something-with-notification user notification)]
      (log/with-context {:result result}
        (if success?
          (log/info "success")
          (log/error "problem with notification processing"))))))
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
  <logger name="bananas.core" level="WARN" />
  <logger name="taskmaster.async" level="ERROR" />
  <logger name="somethnig.else" level="ERROR" />
  <logger name="noisy.web-server" level="OFF" />
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
- [ ] Improve performance of adding/removing keys from the MDC - see Cambium's source for a good approach
