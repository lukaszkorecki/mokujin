# Mokujin

<img src="https://static.wikia.nocookie.net/topstrongest/images/1/15/Mokujin_TTT2.png/revision/latest/scale-to-width-down/1000?cb=20200503180655" align="right" height="250" />

> Mokujin (木人 Wooden person?) is a character in the Tekken series of fighting games. It is a spiritually sensitive animated training dummy,
> and serves as a guardian of good against supernatural evil threats.
> Mokujin is made of logs, hence the name.

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/mokujin.svg)](https://clojars.org/org.clojars.lukaszkorecki/mokujin)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/mokujin-logback.svg)](https://clojars.org/org.clojars.lukaszkorecki/mokujin-logback)


> [!WARNING]
> While Mokujin has been used in live, production applications - it's still under *active* development,
> consider the API stable-ish, with possible small changes here and there. See [the roadmap](#todo) section.

## Just enough (structured) logging

### Quick example


```clojure
;; assuming that log4j2 or logback dependencies & configs are on the classpath, or you're using mokujin-logback
(require '[mokujin.log :as log])


;; just a message
(log/info "hi!")

;; logging exceptions
(try
   ....
   (catch Exception e
     (log/error e "something went wrong")))


;; pass a context map to add structured data to the message
(log/info "hi!" {:foo "bar" :baz "qux"})

;; context can be set
(log/with-context {:action "vibe-check"}
  (log/debug "checking the vibes")
  (let [are-you-ok? (do-the-vibe-check)]
    (if are-you-ok?
      (log/info "hi!")
      (log/warn "something is up!"))))
```


### Rationalle

`clojure.tools.logging` is a Good Enough :tm: solution for majority of logging needs. It's fast, works with any
logging backend supported on the JVM and is simple to use.

The only area where it falls short is producing structured logs.

Sidebar: In the past I'd use [`logfmt`-like](https://brandur.org/logfmt) formatting style when using `infof` (and friends) to produce logs.
Then I'd configure the log ingester (like Fluentd) to parse log lines using this format. That's fine for simple cases, but as soon as
exceptions and  stack traces got thrown into the mix, I fell into the deep rabbit hole of multi-line log parsers and my sanity never quite made it back.

What we want instead is descriptive log messages, with the ability to attach structured data to them.

This is where [Mapped Diagnostic Context (MDC)](https://logback.qos.ch/manual/mdc.html) comes in. Combined with different logging backends and appenders, we can produce easy to read logs during dev time, and emit logs as data in production.

Second part of ensuring that right things are logged and we keep a good performance profile is restricting how logging is done, and what information is included in log events:

- prohibit var-arg dispatch
- only flat maps are allowed in the log context
- discourage use of `printf` style logging, unless strictly necessary

### How does it work?

Mokujin wraps `clojure.tools.logging` and injects SLF4J's MDC into logging events, if provided.
Keep in mind that `infof` (and friends) variants are present, but do not support passing the MDC (more on that later).


#### Performance

While effort was made to keep things as efficient as possible, there is some impact
introduced by manipulating the MDC object. Typical direct call to `clojure.tools.logging/info` is measured in nano-seconds.

A call to `mokujin.log/info` will have nearly the same performance characteristics. Only when introducing several levels of MDC
processing you can expect a small slow down but still maintain sub-microsecond performance. This is absolutely fine for
your typical usage - most of applications running out there do a lot of I/O, where processing times are measured in milliseconds
or seconds even, so any overhead introduced by logging is negligble.

You can run the benchmark via `clj -M:benchmark`. Latest results (as of 19/08/2025) run on my M4 MBP Pro + Java 24 + Clojure 1.12.1:

```
#'mokujin.log-bench/mokujin-log : 38.617807 ns
#'mokujin.log-bench/mokujin-log+context : 182.073844 ns
#'mokujin.log-bench/tools-logging-log : 39.924598 ns
#'mokujin.log-bench/tools-logging-log+context : 104.985398 ns
```

> [!NOTE]
> Benchmarks are tricky, and always should be taken with a grain of salt.
> My sole focus with these is to ensure that Mokujin keeps up with `tools.logging` as far as performance is concerned.
> There's many more variables that need to be taken into account when measuring performance impact of logs in your application.

## API & Usage

### Full API

```clojure
(log/info [msg] [msg ctx])
(log/warn [msg] [msg ctx])
(log/debug [msg] [msg ctx])
(log/trace [msg] [msg ctx])
(log/error [msg] [exc msg] [exc msg ctx])
(log/infof [fmt & fmt-args])
(log/warnf [fmt & fmt-args])
(log/debugf [fmt & fmt-args])
(log/tracef [fmt & fmt-args])
(log/errorf [fmt & fmt-args])
(log/with-context [ctx & body])
```

Just like `clojure.tools.logging`, Mokujin preserves caller context, and ensures that the right
information (namespace, line numbers) is attached to the log event.

The API is close enough that Mokujin is *almost* a drop-in replacement for `c.t.logging`, **however** to promote good practices, and
maintain good performance logging functions that support format strings e.g. `log/infof` or `log/errorf` **do not support the context map** as input.

That's because in 99% of the cases where I'd use `log/infof` what I wanted to do was `(log/info "message" context)` instead.
In cases where you really, really, really want to use formatted strings and the context, this Works Just Fine :tm: :

```clojure
(log/with-context {:some :ctx}
  (log/infof "thing %s happened to %s" thing-a thing-b))
```

Second difference is that only 1- and 2-arity (or in case of `log/error` 3-arity) log functions are suported, so:

```clojure
;; work's in clojure.tools.logging, but will throw an exception in Mokujin

(log/info "hello" "there" "world")
```

To help with migration and good log hygine Mokujin ships with custom hooks for `clj-kondo` and
report warnings in case of suspicious or incompatible call styles are detected.


### The context


The context is a `Map[String]String` internally. To make it easier to work with standard log ingestion infrastructure,
all keywords present in the keys and values are converted to `"snake_case"` strings. Other value types are stringified.
This frees you up from figuring out what can and cannot be serialized by the appender that your logging backend is using.
If you need to change the layout of the produced log line, you can and should defer it to the appender configuration instead.

> [!WARNING]
> Nested maps or other complex data types are not allowed - this is something that might change in the future.


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

MDC is bound to the current thread and won't be propagated to other threads e.g. when dispatching a future or a thread pool task from a Ring handler.

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


## Setup

First of all you need to include Mokujin as your dependency. Second step is to use a logging backend that supports MDC.
Most popular choices are Logback and Log4j2. See `examples` directory for both.

Once you have your logging backend set up, you can start using Mokujin by using `mokujin.log` namespace.

### Migrating from `clojure.tools.logging`

Pretty simple, just replace `clojure.tools.logging` with `mokujin.log` in your `ns` declaration and you're mostly there. Run `clj-kondo --lint .` and look for
warnings or errors in `:mokujin.log` keyword namespace

### Logback

`mokujin-logback` is a sister library which provides a set of helpers to configure Logback at run time using EDN.
 You can of course keep using your hand-crafted, artisanal XML files if you have them already. Both legacy and new XML formats are supported.

To make it easy, Mokujin offers a couple of configuration presets for quick setup, which are suitable for most use cases.
Provided 'presets':

- `:mokujin.logback/text` - plain text appender, will log all standard fields plus MDC
- `:mokujin.logback/json` - powered by Logstash appender, emits log events as JSON with MDC fields merged in
- `:mokujin.logback/json-async` - same as above, but uses async appender which buffers events before rednering them in a background thread, useful for high log volumes

```clojure
;; assuming both mokujin and mokujin-logback are in your classpath
(require '[mokujin.log :as log]
         '[mokujin.logback :as logback])


;; in your REPL init code
(mokujin.logback/configure! {:config :mokujin.logback/text})


;; in `core` namespace of your application:
(mokujin.logback/configure! {:config :mokujin.logback/json
                             ;; you can specify package names or namespaces here to control log levels outside of global setting
                             :logger-filters {"org.eclipse.jetty" "ERROR"}})

;; completely custom configuration as EDN:
(def log-config
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


;; XML files (both pre 1.3 and 1.3+ formats are supported) - as long as logback.xml and/or logback-test.xml is in your classpath
;; they will be loaded automatically, but you can also specify a custom one at run time:
(mokujin.logback/configure! {:config (io/resource "logback.xml")})
```


Logback's configuration system is very powerful, and provides several features, including MDC processing, log rotation, sanitization and more.
This way we can delegate things like redacting MDC or async appenders to Logback, and keep Mokujin focused on providing streamlined API.

Check `:mokujin.logback/json-async` configuration preset for a good example of how to set up async appenders with MDC support.

## TODO

- [x] Maven release
- [x] Improve performance of adding/removing keys from the MDC - see Cambium's or Aviso-logging source for a good approach
- [x] Finalize `log/error` API - it works, but there are cases where its usage can be confusing
- [x] Split library into core and logback-specific components
- [ ] timing support - some form of `with-timing` macro or an arg on `with-context`
- [ ] Provide a way to customize how context data is transformed?
