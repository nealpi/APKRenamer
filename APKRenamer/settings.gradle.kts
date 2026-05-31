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
        // ARSCLib / APKEditor 发布在 JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "APKRenamer"
include(":app")
