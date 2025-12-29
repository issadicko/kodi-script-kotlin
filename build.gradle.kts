plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
}

group = "com.kodi"
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("KodiScript")
                description.set("KodiScript v1.2 interpreter for Kotlin/Spring Boot")
                
                developers {
                    developer {
                        id.set("kodi")
                        name.set("Kodi Team")
                    }
                }
            }
        }
    }
}
