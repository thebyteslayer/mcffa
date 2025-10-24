plugins {
    kotlin("jvm") version "1.9.21"
}

group = "com.thebyteslayer.minecraft.event.ffa"
version = "1.0.0"
description = "Event FFA Plugin for Paper 1.21.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "21"
    }

    val fatJar by registering(Jar::class) {
        dependsOn("compileKotlin")
        archiveClassifier.set("")

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(sourceSets.main.get().output)
        from(configurations.runtimeClasspath.get().map { zipTree(it) })

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        manifest {
            attributes["Main-Class"] = "com.thebyteslayer.minecraft.event.ffa.EventFFAPlugin"
        }
    }

    build {
        dependsOn(fatJar)
    }
}

kotlin {
    jvmToolchain(21)
}
