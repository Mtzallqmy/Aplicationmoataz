pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AlaserAI"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:security",
    ":core:filesystem",
    ":core:terminal",
    ":core:sandbox",
    ":ai:providers",
    ":agent:runtime",
    ":integration:telegram",
    ":integration:mcp",
)
