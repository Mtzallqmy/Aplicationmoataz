plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:filesystem"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    testImplementation(libs.junit)
}
