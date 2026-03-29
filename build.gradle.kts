plugins {
    java
    id("org.openapi.generator") version "7.6.0"
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
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation(platform("software.amazon.awssdk:bom:2.25.29"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:s3")
    compileOnly("jakarta.annotation:jakarta.annotation-api:2.1.1")
    annotationProcessor("com.google.dagger:dagger-compiler:2.51.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sourceSets {
    named("main") {
        java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    }
}

openApiGenerate {
    generatorName.set("java")
    inputSpec.set("$projectDir/src/main/resources/openapi/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    modelPackage.set("com.officialpapers.api.generated.model")
    apiPackage.set("com.officialpapers.api.generated.api")
    invokerPackage.set("com.officialpapers.api.generated.invoker")
    globalProperties.set(
        mapOf(
            "models" to "",
            "apis" to "false",
            "supportingFiles" to "false",
            "modelDocs" to "false",
            "modelTests" to "false",
            "apiDocs" to "false",
            "apiTests" to "false"
        )
    )
    configOptions.set(
        mapOf(
            "library" to "native",
            "dateLibrary" to "java8",
            "serializationLibrary" to "jackson",
            "hideGenerationTimestamp" to "true",
            "openApiNullable" to "false",
            "useJakartaEe" to "true",
            "useBeanValidation" to "false"
        )
    )
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
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
