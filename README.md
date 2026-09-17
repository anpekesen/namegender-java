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
  <version>v0.4.1</version>
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
- `bestGuess(boolean)`: return the likelier gender instead of `unknown` below the confidence threshold.
- `aiFallback(boolean)`: ask a language model when the name is not in the dataset.
  The account must give AI consent in the dashboard first, otherwise the API
  answers 422 `ai_consent_required`.

## Account

```java
var account = client.account(); // costs no credits
System.out.println(account.creditsRemaining() + " credits, " + account.freeToday() + " free today");
```

## Results

A `Result` has `query()`, `name()`, `firstName()`, `middleName()`, `lastName()`, `nameType()`, `gender()`, `country()`, `probability()`,
`sampleSize()`, `tookMs()`, `source()`, `confidence()` and `matchedAs()`, plus
`creditsCharged()`, `creditsRemaining()`, `dataVersion()` and `requestId()`.
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
