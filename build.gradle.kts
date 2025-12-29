plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
}

group = "com.kodi"
version = "1.2.1"

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
                url.set("https://github.com/issadicko/kodi-script-kotlin")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("kodi")
                        name.set("Kodi Team")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/issadicko/kodi-script-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com/issadicko/kodi-script-kotlin.git")
                    url.set("https://github.com/issadicko/kodi-script-kotlin")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/issadicko/kodi-script-kotlin")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                password = System.getenv("PACKAGE_TOKEN") ?: project.findProperty("gpr.key") as String? ?: ""
            }
        }
    }
}
