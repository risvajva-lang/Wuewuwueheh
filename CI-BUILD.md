# CineView VIP — GitHub Actions build

هذا المستودع هو مشروع Android Native مستقل، وليس React Native. لذلك ملفات Gradle و`.github` موجودة في جذر المستودع، وليس داخل `android/`.

## البناء

- يدعم `push` إلى `main` أو `master`.
- يدعم `workflow_dispatch` من تبويب Actions.
- يثبت JDK 17 وAndroid SDK 35 وGradle 8.10.2.
- يتحقق من الملفات الأساسية قبل البناء.
- ينفذ `clean assembleDebug`.
- يتحقق من توقيع Debug APK باستخدام `apksigner`.
- يرفع `app-debug.apk` وملف SHA-256 كـArtifact.

لا يحتاج Debug build إلى Personal Access Token أو Keystore.

> Firebase اختياري وقت التشغيل: عدم وجود `google-services.json` لا يمنع تجميع المشروع لأن Firebase تتم تهيئته ديناميكيًا فقط إذا كانت خيارات Firebase متاحة.


## 2.0.5 verification notes
- Fixed nullable signing certificate handling in IntegrityChecker.
- Removed main-thread runBlocking from NativeBridge APK update; download/install now runs asynchronously.
- Disabled automatic JavaScript popup opening and third-party cookies by default.
- XML/JSON parsing and source brace-balance checks pass in the packaging audit.
