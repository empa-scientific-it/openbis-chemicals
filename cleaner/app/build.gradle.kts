import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.argumentsWithVarargAsSingleArray

/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin application project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/6.9.3/userguide/building_java_projects.html
 */
plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin)
    //Plugin for serialisation
    alias(libs.plugins.serialisation)
    // Apply the application plugin to add support for building a CLI application in Java.
    application

}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    // Openbis
    ivy {
        url = uri("https://sissource.ethz.ch/openbis/openbis-public/openbis-ivy/-/raw/main/")
        patternLayout {

            artifact("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]")
            ivy("[organisation]/[module]/[revision]/ivy.xml")
        }
    }
}



dependencies {
    // Align versions of all Kotlin components
    implementation(platform(libs.kotlin.bom))

    // Use the Kotlin JDK 8 standard library.
    implementation(libs.kotlin.stdlib)

    // Use the Kotlin test library.
    testImplementation(libs.kotlin.test)

    // Use the Kotlin JUnit integration.
    testImplementation(libs.kotlin.test.junit)



    //Dataframes in kotlin
    implementation(libs.kotlinx.dataframe)

    //openBIS API
    implementation(libs.openbis)

    // Kotlinx for serialization
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.properties)


    //Reflection
    implementation(libs.kotlin.reflect)


    //Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)


    //Command line parsing
    implementation(libs.kotlinx.cli)

    //SDF file reader
    implementation(libs.openchemlib)

    //SQLLite
    implementation(libs.sqllite.jdbc)

    //ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    //Logging
    implementation(libs.slf4j)
}

application {
    // Define the main class for the application.
    mainClass.set("cleaner.app.AppKt")
    applicationDefaultJvmArgs = listOf("-Dhttps.protocols=TLSv1")
}



tasks.withType<Test> {
    this.testLogging {
        this.showStandardStreams = true
    }
}

// compile bytecode to java 11 (default is java 6)
kotlin {
    jvmToolchain {
        val javaVer: String by project
        languageVersion.set(JavaLanguageVersion.of(javaVer))
    }
}

tasks.named("run") {
    doFirst {
        val cmdArgs = listOf(
            "../../data/exportedTableAllColumnsAllPages.tsv",
            "../config/molecule.json",
            "../../data/ChEBI_complete.sdf",
            "../../data/chemicals.sqlite",
            "../config/dest_molecule.json",
            "/MATERIALS/GENERAL_MATERIALS/PRODUCTS",
            "--reimport"
        )
        val res = this.setProperty("args", cmdArgs)

    }
}