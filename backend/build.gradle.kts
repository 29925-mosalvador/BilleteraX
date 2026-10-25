plugins {
    java
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "ec.edu.espe"
version = "1.0.0"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    val testTemp = layout.buildDirectory.dir("tmp/tests")
    doFirst {
        testTemp.get().asFile.mkdirs()
        val mockitoAgent = configurations.testRuntimeClasspath.get().single { it.name.startsWith("mockito-core-") }
        jvmArgs("-javaagent:${mockitoAgent.absolutePath}")
    }
    systemProperty("java.io.tmpdir", testTemp.get().asFile.absolutePath)
}
tasks.bootJar { archiveFileName.set("billeterax.jar") }

// The same dependency-free web client is served by Debian and GitHub Pages.
tasks.processResources { from("../web/dist") { into("static") } }
