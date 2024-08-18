# mnemonic4j

[![Test and Deploy](https://github.com/lightsail-network/mnemonic4j/actions/workflows/test-deploy.yml/badge.svg?branch=main)](https://github.com/lightsail-network/mnemonic4j/actions/workflows/test-deploy.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/network.lightsail/mnemonic4j)](https://central.sonatype.com/artifact/network.lightsail/mnemonic4j)
[![javadoc](https://javadoc.io/badge2/network.lightsail/mnemonic4j/javadoc.svg)](https://javadoc.io/doc/network.lightsail/mnemonic4j)

Java implementation of [BIP-0039](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki): Mnemonic code for
generating deterministic keys.

## Overview

This library provides an implementation of the Bitcoin BIP-0039 specification for generating mnemonic sentences and
converting them into binary seeds. The generated seeds can be used to create deterministic wallets using BIP-0032 or
similar methods.

## Installation

To use this library in your Java project, add the following dependency to your `pom.xml` file:

```xml

<dependency>
    <groupId>network.lightsail</groupId>
    <artifactId>mnemonic4j</artifactId>
    <version>0.1.1</version>
</dependency>
```

If you are using Gradle, add the following dependency to your `build.gradle` file:

```kotlin
dependencies {
    implementation("network.lightsail:mnemonic4j:0.1.1")
}
```

## Documentation

Full documentation for this library can be found on:

- [Javadoc](https://javadoc.io/doc/network.lightsail/mnemonic4j)
- [GitHub Pages (latest release)](https://lightsail-network.github.io/mnemonic4j/)

## Usage

### Generating Mnemonic Sentence

To generate a mnemonic sentence, create an instance of the `Mnemonic` class and call the `generate` method, specifying
the desired strength (128 - 256):

```java
Mnemonic mnemonic = new Mnemonic();
String words = mnemonic.generate();
```

### Converting Mnemonic to Seed

To convert a mnemonic sentence into a binary seed, use the `toSeed` method:

```java
String words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
String passphrase = "passw0rd";
Mnemonic mnemonic = new Mnemonic();
byte[] seed = Mnemonic.toSeed(words, passphrase);
```

### Checking Mnemonic Validity

To check if a mnemonic sentence is valid, use the `check` method:

```java
String words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
boolean valid = new Mnemonic().check(words);
```

### Retrieving Original Entropy

To retrieve the original entropy from a mnemonic sentence, use the toEntropy method:

```java
Mnemonic mnemonic = new Mnemonic();
byte[] entropy = mnemonic.toEntropy(words);
```

### Multiple Languages and custom word lists support

The library supports multiple languages and custom word lists. To generate a mnemonic sentence in a specific language,
use the `Language` enum:

```java
Mnemonic mnemonic = new Mnemonic(Language.CHINESE_SIMPLIFIED, null);
String words = mnemonic.generate();
```

To use a custom word list, pass the list of words as a `List<String>` to the `Mnemonic` constructor:

```java
List<String> customWords = Arrays.asList("word0", "word2", "word3", ...);
Mnemonic mnemonic = new Mnemonic(Language.ENGLISH, customWords);
String words = mnemonic.generate();
```

## Android Support

The library works well on Android platforms:

- For Android platforms with API level 26 and above, no additional configuration is required.
- For versions below Android API 26, due to the lack of support for
  certain necessary cryptographic algorithms, you need to include the following dependency:

    ```kotlin
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    ```

  Additionally, add the following lines of code to your Android project:

    ```kotlin
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    Security.addProvider(BouncyCastleProvider() as Provider?)
    ```

## License

This library is released under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0).

## Acknowledgements

This project was inspired by and builds upon the excellent work done in
the [trezor/python-mnemonic](https://github.com/trezor/python-mnemonic) project.