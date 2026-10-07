plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "me.sheimi.sgit"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.manichord.mgit"
        // JGit 7 needs Java 17 library APIs, which Android provides from API 34
        minSdk = 34
        targetSdk = 36

        vectorDrawables.useSupportLibrary = true

        versionCode = 240
        versionName = "1.7.0"
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            if (project.hasProperty("special")) {
                keyAlias = project.property("alias") as String
                keyPassword = project.property("password") as String
                storeFile = file(project.property("keystore") as String)
                storePassword = project.property("password") as String
            } else {
                keyAlias = ""
                keyPassword = ""
                storeFile = file("/empty")
                storePassword = ""
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.jcraft" && requested.name == "jsch") {
            useTarget("com.github.mwiede:jsch:2.28.7")
        }
    }
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
}

dependencies {
    val acraVersion = "5.8.4"
    val jgitVersion = "7.8.0.202609011348-r"

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("androidx.fragment:fragment:1.4.0")
    implementation("androidx.annotation:annotation:1.3.0")
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.2")

    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    kapt("androidx.lifecycle:lifecycle-compiler:2.4.0")

    implementation("com.jakewharton.timber:timber:4.5.1")
    implementation("com.github.mwiede:jsch:2.28.7")
    implementation("commons-io:commons-io:2.22.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.jsch:$jgitVersion")
    implementation("com.nostra13.universalimageloader:universal-image-loader:1.9.5")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    implementation("ch.acra:acra-mail:$acraVersion")
    implementation("ch.acra:acra-dialog:$acraVersion")

    debugImplementation("com.facebook.stetho:stetho:1.5.0")
    debugImplementation("com.facebook.stetho:stetho-timber:1.5.0")
    testImplementation("junit:junit:4.12")
    testImplementation("org.robolectric:robolectric:3.5")
    testImplementation("org.robolectric:shadows-support-v4:3.4-rc2")
}
