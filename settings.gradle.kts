pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter {
                includeGroup("com.github.Kyant0")
                includeGroup("com.github.franmontiel")
            }
        }
    }
}

rootProject.name = "AHUTong"
include (":app")
include (":core:model")
include (":core:common")
include (":core:designsystem")
include (":core:network")
include (":core:storage")
include (":core:auth")
include (":core:plugin-api")
include (":core:plugin-platform")
include (":data:personalization")
include (":data:schedule")
include (":data:grade")
include (":data:chaoxing")
include (":data:repository-index")
include (":feature:repository-index")
include (":feature:settings")
include (":feature:schedule")
include (":feature:grade")
include (":feature:xuexiaotong")
include (":data:recharge")
include (":data:update")
include (":background")
include (":feature:recharge")
include (":feature:circle")
