# CineView Android — تحويل جذري مع الحفاظ على الواجهة

هذا المشروع يحوّل طبقة CineView الحالية إلى تطبيق Android فعلي، مع إبقاء واجهة React/CSS كما هي وعدم إعادة تصميمها.

## المعمارية
- Android native shell (Java entry point + Kotlin feature modules)
- WebView مع AndroidX WebKit
- WebViewAssetLoader لتقديم ملفات الواجهة محليًا
- JavaScript bridge: مشاركة وفتح روابط خارجية ومعلومات runtime
- HTTPS-only network policy
- Deep link scheme: `cineview://`
- Back navigation native
- Local DOM storage للاستمرار في عمل التخزين الحالي
- تشغيل واجهة CineView الحالية داخل WebView مع Native Bridge؛ لا يتم اعتراض `/wp-json/*` أو إعادة كتابته داخل Android.

## بناء APK
يتطلب Android Studio حديثًا أو Gradle 8.10.2 مع JDK 17 وAndroid SDK 35.

```bash
gradle :app:assembleDebug
```

## قبل الإنتاج
1. غيّر `backendUrl` إلى نطاق الإنتاج الفعلي.
2. استخدم keystore توقيع إنتاجي.
3. اختبر كل REST endpoints من جهاز حقيقي.
4. اختبر تسجيل الدخول/Trakt، التشغيل، الاستئناف، المشاركة، والـdeep links.
5. لا تعتمد على فحص ثابت وحده لإثبات التشغيل 100%؛ يجب اختبار APK على جهاز Android فعلي وbackend حي.

## Native feature layer
This build adds a native Media3 player with MediaSession background playback, Android DownloadManager downloads, notification channels and FCM service hooks, Firebase Analytics/Crashlytics/Messaging SDK hooks, HTTPS-only APK update download/install with SHA-256 verification, local JS update storage primitives, and runtime integrity checks (debuggable/emulator/installer/signing certificate). The existing web UI assets and visual design are preserved.

Firebase is intentionally initialized only when a valid Firebase configuration is supplied by the app environment. No `google-services.json` is embedded in this archive.


## CI build gate
The GitHub workflow runs repository/source checks, JavaScript syntax checks when Node is available, JVM unit tests, then `clean assembleDebug`, APK existence verification, and `apksigner verify`. A successful workflow is the authoritative CI build check; local static inspection alone is not a substitute for Android SDK compilation.
