import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 비밀 값은 저장소에 올라가지 않는 local.properties 에서 읽는다 (.gitignore 에 포함됨)
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String): String = localProps.getProperty(name, "").trim()

android {
    namespace = "com.cheon.ccbuswidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cheon.ccbuswidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // 네이버 아이디로 로그인 (developers.naver.com 에서 발급) · 즐겨찾기 동기화 서버 주소 (sync-server)
        buildConfigField("String", "NAVER_CLIENT_ID", "\"${localProp("naver.client.id")}\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET", "\"${localProp("naver.client.secret")}\"")
        buildConfigField("String", "SYNC_URL", "\"${localProp("sync.url")}\"")
    }

    buildTypes {
        release {
            // 릴리스 빌드에서 안 쓰는 코드 · 리소스를 걷어내고 최적화한다 (앱 크기 ↓, 실행 속도 ↑).
            // 디버그 빌드(Android Studio 실행 버튼)에는 영향 없음.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 디버그 키로 서명해서 ▶ 실행 버튼으로도 릴리스 빌드를 바로 깔 수 있게 한다.
            // (Build Variants 창에서 app 을 release 로 바꾸면 됨. 스토어에 올릴 때는 이 줄을 실제 키로 바꿀 것)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // 앱을 켤 때 스플래시 화면 (안드로이드 12 미만에서도 같은 모양)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 네이버 지도
    implementation("com.naver.maps:map-sdk:3.23.3")
    // 네이버 아이디로 로그인 (계정 연동 · 즐겨찾기 동기화)
    implementation("com.navercorp.nid:oauth:5.10.0")
}
