buildscript {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
    dependencies {
        classpath("org.jetbrains.intellij.plugins:gradle-intellij-plugin:0.7.2")
    }
}

plugins {
    java
    id("org.jetbrains.intellij") version "0.7.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

intellij {
    version = "2020.2"
    type = "IU"
    // 只用平台自带的 java 支持, 不引入 Spring/Database 等插件依赖
    setPlugins("java")
    pluginName = "BeanForge"
    sandboxDirectory = "${rootProject.rootDir}/idea-sandbox"
    updateSinceUntilBuild = false
}

group = "com.nuo.beanforge"
version = "0.1.3"

repositories {
    mavenLocal()
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    mavenCentral()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
