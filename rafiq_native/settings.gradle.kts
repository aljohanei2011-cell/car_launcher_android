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

// لانشر مستقل عن «رفيق الطريق» القديم: مشروع ومعرّف تطبيق منفصلان، فيُثبَّت بجانبه
rootProject.name = "RafiqNative"
include(":app")
