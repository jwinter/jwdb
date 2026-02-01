plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.google.protobuf") version "0.9.4"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencies {
    // Protobuf dependencies
    implementation("com.google.protobuf:protobuf-kotlin:4.29.2")
    implementation("com.google.protobuf:protobuf-java:4.29.2")

    // Netty dependencies
    implementation("io.netty:netty-all:4.1.115.Final")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("testUnit") {
    group = "verification"
    description = "Runs unit tests (fast, isolated tests)"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("unit")
    }
}

tasks.register<Test>("testIntegration") {
    group = "verification"
    description = "Runs integration tests (tests with some dependencies)"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("integration")
    }
}

tasks.register<Test>("testE2e") {
    group = "verification"
    description = "Runs end-to-end tests (full system tests)"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("e2e")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.2"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}

ktlint {
    filter {
        exclude { projectDir.toURI().relativize(it.file.toURI()).path.contains("/generated/") }
    }
}

kover {
    reports {
        filters {
            excludes {
                packages("com.example.proto")
            }
        }
    }
}
