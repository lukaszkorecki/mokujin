# Mokujin

<img src="https://static.wikia.nocookie.net/topstrongest/images/1/15/Mokujin_TTT2.png/revision/latest/scale-to-width-down/1000?cb=20200503180655" align="right" height="250" />

> Mokujin (木人 Wooden person?) is a character in the Tekken series of fighting games. It is a spiritually sensitive animated training dummy, and serves as a guardian of good against supernatural evil threats. Mokujin is made of logs, hence the name.



[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/mokujin.svg)](https://clojars.org/org.clojars.lukaszkorecki/mokujin)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/mokujin-logback.svg)](https://clojars.org/org.clojars.lukaszkorecki/mokujin-logback)



> [!WARNING]
> While Mokujin has been used in live, production applications - it's still under *active* development,
> consider the API stable-ish, with possible small changes here and there. See [the roadmap](#todo) section.

## Just enough (structured) logging

### Quick example


```clojure
;; assuming that sl4j and log4j2 or logback are on the classpath
(require '[mokujin.log :as log])


(log/info "hi!")

(try
   ....
   (catch Exception e
     (log/error e "something went wrong")))


;; pass a context map
(log/info "hi!" {:foo "bar" :baz "qux"})

(log/with-context {"my-key" "my-value"}
  (log/info "hi!"))
```


### Rationalle

`clojure.tools.logging` is a Good Enough :tm: solution for majority (if not all) your logging needs. It's fast, works with any
logging backend supported on the JVM and is incredibly simple to use.

The only area where it falls short is producing structured logs.

In the past I'd use [`logfmt`-like](https://brandur.org/logfmt) formatting style when using `infof` (and friends) to produce logs.
Then I'd configure the log ingester (like FluentD) to parse log lines using this format. That's fine for simple cases, but as soon as
exceptions (and stack traces) got thrown into the mix, I fell into the deep rabbit hole of multi-line log
parsers in Fluentd and vector.dev.

This is where [Mapped Diagnostic Context (MDC)](https://logback.qos.ch/manual/mdc.html) comes in. It's a way to attach structured data to your logs, that can be used by the log collector

This way you get both structured data that can be used to dervive metrics, alerts etc in your log collector and human readable logs for debugging pruposes.

Mokujin emerged after years of working with different logging solutions in Clojure (and other languages), log aggregation
systems and monitoring platforms. It strikes a balance between familiar API, and leveraging existing
JVM logging ecosystem and all of its good (and less good) parts.

### How does it work?

Mokujin wraps `clojure.tools.logging` and inject SLF4J's MDC into logging events, if provided. It tries to maintain a balance between new
features and the API of a widely used library. Mokujin wraps standard logging macros (`info`, `warn` etc) and adds
support for the context map argument, as the first argument.

Keep in mind that `infof` (and friends) variants are present, but do not support passing the MDC (more on that later).


#### Performance

While effort was made to keep things as efficient as possible, there is some impact
introduced by manipulating the MDC object. Typical "raw" call to `clojure.tools.logging/info` is measured in nano-seconds.
Same call to `mokujin.log/info` will have nearly the same performance characteristics. Only when introducing several levels of MDC
processing you can expect a small slow down but still maintain sub-microsecond performance. This is absolutely fine for
your typical usage - most of applications running out there do a lot of I/O, where processing times are measured in milliseconds
or seconds even, so any overhead introduced by logging is negligble.

You can run the benchmark via `clj -M:benchmark`. Latest results (as of 30/03/2025) are:

```
#'mokujin.log-bench/mokujin-log : 76.876114 ns
#'mokujin.log-bench/mokujin-log+context : 346.391248 ns
#'mokujin.log-bench/tools-logging-log : 78.078607 ns
#'mokujin.log-bench/tools-logging-log+context : 221.394879 ns
```

Macbook M4 Pro.

> [!INFO]
> Benchmarks are tricky, and always should be taken with a grain of salt.
> My sole focus with these is to ensure that Mokujin keeps up with `tools.logging` as far as performance is concerned.
> There's many more variables that need to be taken into account when measuring performance impact of logs in your application.

## API & Usage

The API is close enough that Mokujin is *almost* a drop-in replacement for `c.t.logging`, **however** to force good practices,
logging functions that support format strings e.g. `log/infof` or `log/errorf` **do not suport the context map**.

That's because in 99% of the cases where I'd use `log/infof` what I wanted to do was `(log/info "message" context)` instead.
In cases where you really really want to use formatted strings and the context, this Works Just Fine :tm: :

```clojure
(log/with-context {:some :ctx}
  (log/infof "thing %s happened to %s" thing-a thing-b))
```

### The context


The context is a `Map[String]String` internally. To make it easier to work with standard log ingestion infrastructure,
all keywords present in the keys and values are converted to `"snake_case"` strings. Other value types are stringified.
This frees you up from figuring out what can and cannot be serialized by the appender that your logging backend is using.
If you need to change the layout of the produced log line, you can and should defer it to the appender configuration instead.

> [!WARNING]
> Nested maps or other "rich" data types are not allowed - this is something that might change in the future.


#### `with-context`

You can use `with-context` macro, to set context for a form, request handler body etc.
Contexts can be nested, but see the next section for caveats.

All map keys and values will be stringified using `name` + `str` combo.

```clojure
(log/with-context ctx body)
```

Example:

``` clojure
(defn handle-notification [user notification]
  (log/with-context {:user-id (:id user) :notification-type (:type notification)}
    (let [{:keys [success? response-code] result} (do-something-with-notification user notification)]
      (log/with-context {:response-code response-code}
        (if success?
          (log/info "success")
          (log/error "problem with notification processing"))))))
```

#### Context and threads

Since MDC is thread-bound, the context is also thread-bound. This means that if you're using a thread pool, context won't be passed around:

``` clojure
(log/with-context {:foo "bar"}
  (future
    (log/info "hi!"))) ;; won't have the context
```


> [!INFO]
> This **might change** in the future, but for now, you're better off passing the context explicitly to the function that needs it.


To work around this, you can use `log/with-context` to wrap the form that needs the context:


``` clojure
(log/with-context {:foo "bar"}
  (let [current-context (log/get-context)])
  (future
    (log/with-context current-context
      (log/info "hi!")))) ;; will have the context
```


### Full API

#### Logging statements

```clojure
(log/info [msg] [msg ctx])
(log/warn [msg] [msg ctx])
(log/debug [msg] [msg ctx])
(log/error [msg] [exc msg] [exc msg ctx])
(log/infof [fmt & fmt-args])
(log/warnf [fmt & fmt-args])
(log/debugf [fmt & fmt-args])
(log/errorf [fmt & fmt-args])
(log/with-context [ctx & body])
```

Mokujin preserves caller context, and ensures that the right information (namespace, line numbers) is injected into the log statement, under the `logger` field.

> [!WARNING]
> `log/error` doesn't support the context/MDC argument for 1- and 2-arity variants, again - you're better off wrapping the form in `log/with-context` (see below) and using that as extra information attached to all logs
> carried over with the exception - that's how I enrich exceptions tracked by Sentry's LogBack appender.
> Additionally, figuring out the types of passed in arguments gets complicated as we'd have to distinguish between the context map, exception, message string and rest of the args
> and that introduces overhead that we don't want.
> This is the only area of Mokujin's user-facing API that *might* change, maybe.



## Setup

First of all you need to include Mokujin as your dependency. Second step is to use a logging backend that supports MDC.
Most popular choices are Logback and Log4j2. See `examples` directory for both.

Once you have your logging backend set up, you can start using Mokujin by using `mokujin.log` namespace.

### Migrating from `clojure.tools.logging`

Pretty simple, just replace `clojure.tools.logging` with `mokujin.log` in your `ns` declaration and you're good to go.


### Logback


Mokujin offers a sister library, `mokujin-logback` that provides a way to configure Logback from code, and provides a way to
simplify its configuration. Rather than setting up class paths and XML files, you can configure Logback from code, using EDN.

Further more, Mokujin offers a couple of configuration preset for quick setup, which Work Well Most of the Time :tm:.

```clojure
;; assuming both mokujin and mokujin-logback are in your classpath
(require '[mokujin.log :as log]
         '[mokujin.logback :as logback])


;; in your REPL init code
(mokujin.logback/configure! {:config :mokujin.logback/text})


;; in `core` namespace of your application:
(mokujin.logback/configure! {:config :mokujin.logback/json
                             :logger-filters {"org.eclipse.jetty" "ERROR"}})

;; completely custom conifigration as EDN:

(def l
og-config
  [[:configuration
    [:appender
     {:name "STDOUT"
      :class "ch.qos.logback.core.ConsoleAppender"
      :encoder
      [:pattern
       {:pattern "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"}]}]
    [:root
     {:level "debug"
      :appender-ref
      {:ref "STDOUT"}}]]])

(mokujin.logback/configure! {:config log-config})


;; XML files (both pre 1.3 and 1.3+ formats are supported)
(mokujin.logback/configure! {:config (io/resource "logback.xml")})
```


Logback's configuration system is very powerful, and provides several features, including MDC processing, log rotation, and more.
This way we can delegate things like redacting MDC or async appenders to Logback, and keep Mokujin focused on providing streamlined API.

## TODO

- [x] Maven release
- [x] Improve performance of adding/removing keys from the MDC - see Cambium's or Aviso-logging source for a good approach
- [x] Finalize `log/error` API - it works, but there are cases where its usage can be confusing
- [x] Split library into core and logback-specific components
- [ ] timing support - some form of `with-timing` macro or an arg on `with-context`
- [ ] Provide a way to customize how context data is transformed
