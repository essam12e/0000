# Public Search Center

مركز البحث العام الذكي هو تطبيق عربي RTL مبني بـ Next.js لإنشاء روابط بحث عامة وآمنة عبر منصات مثل Google وYouTube وTikTok وX وFacebook وGitHub وReddit وغيرها، بدون تخزين عمليات البحث وبدون إرسال قيمة البحث إلى أي backend خاص بالموقع.

## المميزات

- واجهة عربية كاملة باتجاه RTL.
- بحث حسب الاسم، اسم المستخدم، البريد الإلكتروني، أو رقم الجوال.
- توليد روابط عامة فقط وفتحها في تبويبات جديدة.
- فلترة محتوى تمنع الاستخدام الضار أو المسيء.
- اكتشاف رمز الدولة تلقائيًا عند إدخال رقم دولي مثل `+966`.
- محدد رموز اتصال دولي شامل عند إدخال رقم محلي.
- زر فتح كل النتائج، نسخ كل الروابط، ومسح البحث.
- وضع فاتح وداكن.
- SEO عربي، Open Graph، Twitter cards، sitemap، robots.
- PWA قابلة للتثبيت مع manifest وservice worker بسيط.
- Vercel Analytics مفعلة عبر `@vercel/analytics/react`.

## الخصوصية والسلامة

التطبيق يعمل بالكامل على جهة العميل لقيم البحث. لا توجد API مخصصة لاستقبال عمليات البحث، ولا يتم حفظ أو إرسال قيم البحث إلى خادم خاص بهذا المشروع. الروابط التي يتم إنشاؤها تفتح منصات خارجية عامة فقط.

## التشغيل المحلي

```bash
npm install
npm run dev
```

ثم افتح:

```text
http://localhost:3000
```

## فحص الإنتاج

```bash
npm run typecheck
npm run build
```

## النشر على Vercel

1. ارفع المشروع إلى GitHub.
2. افتح Vercel واختر Import Project.
3. اختر مستودع GitHub.
4. إعدادات Vercel الافتراضية مناسبة:
   - Framework Preset: Next.js
   - Build Command: `npm run build`
   - Output: يتم ضبطه تلقائيًا
5. بعد النشر، افتح رابط الإنتاج وتأكد من ظهور الصفحة.

## ملاحظات Vercel Analytics

تم تثبيت `@vercel/analytics` وإضافة `<Analytics />` في `app/layout.tsx`. ستبدأ التحليلات بالعمل بعد النشر على Vercel. قد تحتاج إلى زيارة لوحة المشروع في Vercel للتأكد من تفعيل تبويب Analytics حسب إعدادات حسابك.

## رفع المشروع إلى GitHub

من داخل مجلد المشروع:

```bash
git init
git add .
git commit -m "Initial public search center"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

## أوامر مهمة

```bash
npm run dev
npm run typecheck
npm run build
npm run start
```

## لا توجد متغيرات بيئة مطلوبة

لا يحتاج المشروع إلى مفاتيح سرية أو ملفات `.env` للتشغيل الأساسي، لأنه لا يستخدم backend ولا يخزن عمليات البحث.
