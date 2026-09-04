pluginManagement {
    repositories {
        maven {
            url = uri("http://maven.aliyun.com/repository/public")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/google")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/gradle-plugin")
            isAllowInsecureProtocol = true
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("http://maven.aliyun.com/repository/public")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://maven.aliyun.com/repository/google")
            isAllowInsecureProtocol = true
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "OmniSolve AI Mobile Overlay"
include(":app")
