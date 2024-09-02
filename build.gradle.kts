plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.10"
    kotlin("plugin.serialization") version "2.0.10"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.2") // Use the latest version
    implementation("org.jetbrains.kotlinx:dataframe:0.13.1")
    intellijPlatform {
        bundledPlugins("org.jetbrains.kotlin")
        plugins("IdeaVIM:2.15.3", "org.jetbrains.android:242.20224.387")
        create("IC", "2024.2")

        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Kotlin2Promela"
    }
}


tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    
    runIde {
        maxHeapSize = "16G"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
