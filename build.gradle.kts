import java.util.Properties

plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    id("com.vanniktech.maven.publish") version "0.28.0"
}

group = "io.github.issadicko"
version = "0.1.0"

// Load Maven Central credentials from local file
val mavenCentralCredentials = Properties().apply {
    val credentialsFile = file("maven-central/credentials.properties")
    if (credentialsFile.exists()) {
        credentialsFile.inputStream().use { load(it) }
    }
}

// Set credentials as Gradle extra properties
mavenCentralCredentials.stringPropertyNames().forEach { key ->
    val value = mavenCentralCredentials.getProperty(key)
    when (key) {
        "ossrh.username" -> extra["mavenCentralUsername"] = value
        "ossrh.password" -> extra["mavenCentralPassword"] = value
        "signing.keyId" -> extra["signing.keyId"] = value
        "signing.password" -> extra["signing.password"] = value
        "signing.secretKeyRingFile" -> extra["signing.secretKeyRingFile"] = value
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}


mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    
    coordinates(group.toString(), "kodi-script", version.toString())
    
    pom {
        name.set("KodiScript")
        description.set("KodiScript v0.0.1 interpreter for Kotlin/Spring Boot")
        url.set("https://github.com/issadicko/kodi-script-kotlin")
        
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        
        developers {
            developer {
                id.set("issadicko")
                name.set("Issa Hamadou Dicko")
            }
        }
        
        scm {
            connection.set("scm:git:git://github.com/issadicko/kodi-script-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/issadicko/kodi-script-kotlin.git")
            url.set("https://github.com/issadicko/kodi-script-kotlin")
        }
    }
}
