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
  <version>v0.3.0</version>
</dependency>
```

```java
var client = new NameGender(System.getenv("NAMEGENDER_API_KEY"));
var result = client.name("Ayşe", "TR");
System.out.println(result.gender());
```

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
