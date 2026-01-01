plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}


// TODO: Configure your extension here (please change the defaults!)
qupathExtension {
    name = "qupath-extension-fireparser"
    group = "io.github.qupath"
    version = "0.1.0-SNAPSHOT"
    description = "A simple QuPath extension"
    automaticModule = "io.github.qupath.extension.fireparser"
}

// TODO: Define your dependencies here
dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // Add RichTextFX for CodeArea
    implementation("org.fxmisc.richtext:richtextfx:0.11.2")

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)

}
