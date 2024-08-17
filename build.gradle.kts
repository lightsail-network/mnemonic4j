plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.dokka") version "1.9.20"
    id("signing")
    id("maven-publish")
    id("com.gradleup.nmcp") version "0.0.9"
}

group = "network.lightsail"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.0")
}



tasks {
    test {
        useJUnitPlatform()
    }

    val sourcesJar by creating(Jar::class) {
        archiveClassifier = "sources"
        from(sourceSets.main.get().allSource)
    }

    val javadocJar by creating(Jar::class) {
        archiveClassifier = "javadoc"
        dependsOn(javadoc)
        from(javadoc.get().destinationDir) // It needs to be placed after the javadoc task, otherwise it cannot read the path we set.
    }
}

kotlin {
    jvmToolchain(21)
}

artifacts {
    archives(tasks.jar)
    archives(tasks["javadocJar"])
    archives(tasks["sourcesJar"])
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "mnemonic4j"
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            pom {
                name.set("mnemonic4j")
                description.set("Java mnemonic code for generating deterministic keys, BIP39.")
                url.set("https://github.com/lightsail-network/mnemonic4j")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://github.com/lightsail-network/mnemonic4j/blob/master/LICENSE")
                        distribution.set("https://github.com/lightsail-network/mnemonic4j/blob/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("overcat")
                        name.set("Jun Luo")
                        url.set("https://github.com/overcat")
                    }
                    organization {
                        name.set("Lightsail Network")
                        url.set("https://github.com/lightsail-network")
                    }
                }
                scm {
                    url.set("https://github.com/lightsail-network/mnemonic4j")
                    connection.set("scm:git:https://github.com/lightsail-network/mnemonic4j.git")
                    developerConnection.set("scm:git:ssh://git@github.com/lightsail-network/mnemonic4j.git")
                }
            }
        }
    }
}

signing {
    val publishCommand = "publishAllPublicationsToCentralPortal"
    isRequired = gradle.startParameter.taskNames.contains(publishCommand)
    println("Need to sign? $isRequired")
    // https://docs.gradle.org/current/userguide/signing_plugin.html#using_in_memory_ascii_armored_openpgp_subkeys
    // export SIGNING_KEY=$(gpg2 --export-secret-keys --armor {SIGNING_KEY_ID} | grep -v '\-\-' | grep -v '^=.' | tr -d '\n')
    val signingKey = System.getenv("SIGNING_KEY")
    val signingKeyId = System.getenv("SIGNING_KEY_ID")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (isRequired && (signingKey == null || signingKeyId == null || signingPassword == null)) {
        throw IllegalStateException("Please set the SIGNING_KEY, SIGNING_KEY_ID, and SIGNING_PASSWORD environment variables.")
    }
    println("Signing Key ID: $signingKeyId")
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}


nmcp {
    // https://github.com/GradleUp/nmcp
    publishAllProjectsProbablyBreakingProjectIsolation {
        username = System.getenv("SONATYPE_USERNAME")
        password = System.getenv("SONATYPE_PASSWORD")
        // publish manually from the portal
        publicationType = "USER_MANAGED"
        // or if you want to publish automatically
        // publicationType = "AUTOMATIC"
    }
}