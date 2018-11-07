# Spring HTTP Session implementation backed by ScaleOut StateServer

## Introduction

The ScaleOut Spring HTTP Session library for Java allows developers to use ScaleOut StateServer to serve Java HttpSessions from the in-memory data grid to a Spring application. The library implements the spring-session-core Session and SessionRepository interfaces to store HTTP session objects within a specified Namespace in the in-memory data grid. 

Our **[docs](https://scaleoutsoftware.github.io/SpringSessionProvider/index.html)** will get you up and running in no time. 

To use the library in your project:

### Gradle

For Gradle, you can add the ScaleOut API Repository to your build.gradle by adding the following under repositories: 

``` 
repositories {
    mavenCentral()
    maven {
        url "https://repo.scaleoutsoftware.com/repository/external"
    }
}
```

...and then you can add the ScaleOut Spring Session API as a dependency:

```
compile group: 'com.scaleoutsoftware.spring', name: "session", version: '1.0'
```

### Maven

For Maven, you can add the ScaleOut API Repository to your pom.xml by adding the following repository reference: 

```
<repository>
    <id>ScaleOut API Repository</id>
    <url>https://repo.scaleoutsoftware.com/repository/external</url>
</repository>
```

...and then you can add the ScaleOut Spring Session API as a dependency:

```
<dependencies>
	<dependency>
	  <groupId>com.scaleoutsoftware.spring</groupId>
	  <artifactId>session</artifactId>
	  <version>1.0</version>
	</dependency>
</dependencies>
```

This library is open source and has dependencies on other ScaleOut 
Software products. 

License: Apache 2 