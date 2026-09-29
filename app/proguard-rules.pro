-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**

# 네이버 지도 SDK — SDK 가 자체 규칙(consumer rules)을 포함하므로 전체를 붙잡아 두지 않는다 (릴리스 APK 가 작아진다).
# 만약 릴리스 빌드에서 지도가 안 뜨면 아래 줄의 # 을 지우면 된다.
# -keep class com.naver.maps.** { *; }
-dontwarn com.naver.maps.**
# OkHttp 가 선택적으로 찾는 플랫폼 클래스 경고 무시
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 네이버 아이디로 로그인 SDK (SDK 가 자체 규칙을 포함하므로 경고만 끈다)
-dontwarn com.navercorp.nid.**
