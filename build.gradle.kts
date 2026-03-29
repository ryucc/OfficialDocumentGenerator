plugins {
    java
}

group = "com.officialpapers"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.4")
    implementation("com.google.dagger:dagger:2.51.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation(platform("software.amazon.awssdk:bom:2.25.29"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:s3")
    annotationProcessor("com.google.dagger:dagger-compiler:2.51.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Zip>("packageLambda") {
    from(tasks.named("compileJava"))
    from(tasks.named("processResources"))
    into("lib") {
        from(configurations.runtimeClasspath)
    }
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("lambda.zip")
}

tasks.named("build") {
    dependsOn("packageLambda")
}
