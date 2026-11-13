plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.protobuf")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("com.google.protobuf:protobuf-javalite:4.28.3")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    generateProtoTasks {
        all().configureEach {
            builtins.named("java") {
                option("lite")
            }
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateProto")
}
