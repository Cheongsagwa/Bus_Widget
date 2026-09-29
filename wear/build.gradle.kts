plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // 코드 패키지는 따로 두지만, 폰 앱과 applicationId · 서명 키가 같아야 워치와 폰이 서로를 찾는다
    namespace = "com.cheon.ccbuswidget.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cheon.ccbuswidget"
        // Wear OS 3 (갤럭시 워치4) 이상
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 폰 앱과 같은 디버그 키로 서명 (서명이 다르면 폰 ↔ 워치 데이터가 오가지 않는다)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // 폰 ↔ 워치 데이터
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    // 화면 (Compose for Wear OS)
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")

    // 진행 중 활동 (워치 화면 아래 · 최근 앱의 버스 아이콘)
    implementation("androidx.wear:wear-ongoing:1.0.0")

    // 타일
    implementation("androidx.wear.tiles:tiles:1.5.0")
    implementation("androidx.wear.protolayout:protolayout:1.3.0")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")

    // 워치 페이스 컴플리케이션
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
}
