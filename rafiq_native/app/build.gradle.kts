import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aljohanei.rafiq"
    compileSdk = 34

    defaultConfig {
        // معرّف مختلف عن التطبيق القديم (com.example.carlauncher) فيُثبَّتان معاً
        applicationId = "com.aljohanei.rafiq"
        minSdk = 26
        targetSdk = 30
        val code = (System.getenv("VERSION_CODE") ?: "1").toInt().coerceAtLeast(1)
        versionCode = code
        versionName = "1.0.$code"
    }

    // نفس مفتاح التوقيع الثابت إن توفّر في بناء GitHub Actions، حتى تُثبَّت التحديثات فوق بعضها
    val signProps = Properties().apply {
        val f = rootProject.file("signing/signing.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val hasSigning = rootProject.file("signing/release.jks").exists() && signProps.getProperty("storePassword") != null
    signingConfigs {
        if (hasSigning) {
            create("fixed") {
                storeFile = rootProject.file("signing/release.jks")
                storePassword = signProps.getProperty("storePassword")
                keyAlias = signProps.getProperty("keyAlias")
                keyPassword = signProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // تصغير الكود والموارد: ملف أصغر واستهلاك أقل للذاكرة
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasSigning) signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            if (hasSigning) signingConfig = signingConfigs.getByName("fixed")
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

// بدون أي مكتبات خارجية: واجهات أندرويد الأصلية فقط
dependencies {
}
