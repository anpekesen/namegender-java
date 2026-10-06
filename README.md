# NameGender Java

Java 17+ client built on `java.net.http.HttpClient`.

Until the Maven Central namespace is verified, install the tagged release through JitPack:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.anpekesen</groupId>
  <artifactId>namegender-java</artifactId>
  <version>v0.6.0</version>
</dependency>
```

```java
var client = new NameGender(System.getenv("NAMEGENDER_API_KEY"));
var result = client.name("Ayşe", "TR");
System.out.println(result.gender() + " " + result.probability() + "% · sample " + result.sampleSize());
```

## Options

`name`, `email`, `username` and `bulk` also take an `Options` value. It is
immutable, so every call returns a new one:

```java
var result = client.name("Andrea", Options.none().country("IT").bestGuess(true));
var many = client.bulk(List.of("Ayşe", "Mehmet"), Options.none().aiFallback(true));
```

- `country(String)`: a two-letter ISO code; the answer is weighted by that country's data.
- `locale(String)`: a language tag such as `it-IT` or `pt_BR`. Its region is
  used as the country when `country` is not set; a tag without a region (`en`) sets none.
- `ip(String)`: the end user's IP address. Its country is used when neither
  `country` nor a locale with a region is set. The API does not store it.
- `bestGuess(boolean)`: return the likelier gender instead of `unknown` below the confidence threshold.
- `aiFallback(boolean)`: ask a language model when the name is not in the dataset.
  The account must give AI consent in the dashboard first, otherwise the API
  answers 422 `ai_consent_required`.

`country` wins over `locale`, which wins over `ip`. `result.countrySource()`
says which one was used (`country`, `locale` or `ip`), or is null when none
was. In a bulk lookup it is on the `BulkResult` itself, not on each item. `countries` and file jobs take neither
`locale` nor `ip`.

```java
// In a servlet: the visitor's Accept-Language and address
var result = client.name("Andrea", Options.none()
  .locale(request.getLocale().toLanguageTag())
  .ip(request.getRemoteAddr()));
```

## Account

```java
var account = client.account(); // costs no credits
System.out.println(account.creditsRemaining() + " credits, " + account.freeToday() + " free today");
```

## Results

A `Result` has `query()`, `name()`, `firstName()`, `middleName()`, `lastName()`, `nameType()`, `gender()`, `country()`, `probability()`,
`sampleSize()`, `tookMs()`, `source()`, `confidence()` and `matchedAs()`, plus
`countrySource()`, `creditsCharged()`, `creditsRemaining()`, `dataVersion()` and `requestId()`.
Success is the HTTP status: a non-2xx response throws `NameGenderException`,
whose `status()` is the HTTP status and whose message is the raw error body
(`{"error", "message", "request_id", "docs"}`).

## Country distribution

Which countries a name is recorded in. This is not a country-of-origin or
ethnicity inference: `registrations` is counted volume, comparable only among
the countries that publish counted birth statistics, and `attestedIn` is
presence with no weight attached. Show `basis().note()` next to any percentage.

```java
var dist = client.countries("Mehmet", 10); // limit: 1-100, null for the server default of 25
dist.registrations().forEach(r -> System.out.println(r.country() + " " + r.share() + "%"));
System.out.println(String.join(", ", dist.attestedIn()));
```

## File jobs

Upload a CSV or XLSX file (up to 100 MB and 1,000,000 rows) and get it back
with gender columns added. One credit per row, charged only if the job
completes.

```java
var job = client.createBatch(Path.of("customers.csv"),   // or (byte[] content, "customers.csv", options)
  BatchOptions.none()
    .nameColumn("first_name")      // required to start
    .countryColumn("country"));    // optional: a country code per row

var done = client.waitBatch(job.id(), Duration.ofHours(1), j -> System.out.println(j.progress()));
if (done.status().equals("failed")) throw new IllegalStateException(done.error().code());

client.downloadBatch(done.id(), Path.of("customers-gender.csv"));   // or downloadBatch(id) for the bytes
```

`nameColumn` is required to start: a guessed column that turns out to be
wrong would spend credits on the wrong data. To see the columns and the cost
first, upload with `start(false)`, read `job.inspection()`, then call
`client.startBatch(job.id(), BatchOptions.none().nameColumn(...))`.

`createBatch` sends an `Idempotency-Key` and retries network errors and
502/503/504 with the same key, so a retry never opens a second job. Pass your
own `idempotencyKey(...)` to keep that guarantee across your own retries.

`waitBatch` returns a failed job rather than throwing; branch on
`job.error().code()`. `cancelBatch` returns the credit of a job that has not
started, and deletes a finished one. `listBatches(limit, page)` includes jobs
started from the dashboard. Up to three jobs can be queued or running at once;
a fourth is refused with `429 too_many_batches`.

The result appends `gender`, `probability`, `sample_size`, `country`, `source`,
`matched_as`, `first_name`, `middle_name`, `last_name` and `name_type` to every
row. A CSV result starts with a UTF-8 byte order mark so that Excel reads it
correctly; skip the first three bytes, or the `﻿` character, when you
parse it yourself.

## Webhooks

Add an endpoint under Webhooks in the dashboard, and NameGender sends a signed
`POST` to it when a file job completes or fails, and when credits are about to
run out (`credits.low`) or have run out (`credits.depleted`, checked hourly).
`Webhooks.verify` checks the signature and the timestamp, and returns the event.

```java
// Spring: take the body as byte[], not as a parsed object. The signature
// covers the exact bytes sent; parsing and re-serialising changes them.
@PostMapping("/namegender")
ResponseEntity<Void> namegender(@RequestBody byte[] body,
                                @RequestHeader(value = "NameGender-Signature", required = false) String signature) {
  WebhookEvent event;
  try {
    event = Webhooks.verify(body, signature, System.getenv("NAMEGENDER_WEBHOOK_SECRET"));
  } catch (WebhookVerificationException e) {
    return ResponseEntity.badRequest().build();
  }

  switch (event.type()) {
    case "batch.completed", "batch.failed" -> queue(event.batchJob());           // the job, as getBatch() returns it
    case "credits.low", "credits.depleted" -> alert(event.creditsAlert());
    default -> { }                                                               // a type added later: ignore it
  }
  return ResponseEntity.noContent().build();
}
```

Answer quickly and do slow work afterwards. Anything other than a 2xx within 10
seconds is retried, up to 8 attempts over about 45 hours. Use `event.id()`
(also the `NameGender-Event-Id` header) to ignore a delivery you have already
handled: a retry carries the same id, and order is not guaranteed. During a
secret rotation the header carries two `v1` signatures; either one matching is
enough. `WebhookVerificationException` extends `NameGenderException`.
