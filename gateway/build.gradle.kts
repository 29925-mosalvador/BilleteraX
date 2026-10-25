plugins {
    java
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "ec.edu.espe"
version = "1.2.0"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
sourceSets.main { java.srcDir("../protocol/src/main/java") }
tasks.processResources { from("../web/dist") { into("static") } }
tasks.test {
    useJUnitPlatform()
    doFirst {
        val agent = configurations.testRuntimeClasspath.get().single { it.name.startsWith("mockito-core-") }
        jvmArgs("-javaagent:${agent.absolutePath}")
    }
}
tasks.bootJar { archiveFileName.set("gateway.jar") }
