import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.carlauncher"
    // الحد الأدنى Android 9 (API 28)، والهدف الحالي Android 11 (API 30)
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.carlauncher"
        minSdk = 28
        targetSdk = 30
        // في GitHub Actions يُؤخذ من رقم البناء، فيزيد مع كل تحديث
        val code = (System.getenv("VERSION_CODE") ?: "2").toInt().coerceAtLeast(2)
        versionCode = code
        versionName = "2.$code"
        // مكان الإصدارات التي يتحقق منها زر «تحديث التطبيق»
        buildConfigField("String", "UPDATE_REPO", "\"aljohanei2011-cell/car_launcher_android\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // مفتاح توقيع ثابت: بدونه لا يقبل أندرويد تثبيت التحديث فوق النسخة السابقة
    val signProps = Properties().apply {
        val f = rootProject.file("signing/signing.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("signing/release.jks")
            storePassword = signProps.getProperty("storePassword")
            keyAlias = signProps.getProperty("keyAlias")
            keyPassword = signProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
}
