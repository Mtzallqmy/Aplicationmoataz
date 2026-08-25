plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:filesystem"))
    implementation(project(":core:terminal"))
    implementation(project(":ai:providers"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
