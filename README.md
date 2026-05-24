# نطاق | الباحث العام

منصة بحث عامة ذكية وآمنة تساعدك على فتح نتائج البحث العامة عبر عدة منصات بطريقة احترافية ومنظمة. الواجهة عربية بالكامل، تدعم RTL، وتحترم الخصوصية لأن قيم البحث لا تُرسل إلى backend خاص بالمشروع ولا يتم تخزينها.

## المميزات

- واجهة عربية كاملة باتجاه RTL.
- بحث حسب الاسم، اسم المستخدم، البريد الإلكتروني، أو رقم الجوال.
- توليد روابط عامة فقط وفتحها في تبويبات جديدة.
- فلترة محتوى تمنع الاستخدام الضار أو المسيء.
- اكتشاف رمز الدولة تلقائيًا عند إدخال رقم دولي مثل `+966`.
- محدد رموز اتصال دولي شامل عند إدخال رقم محلي.
- زر فتح كل النتائج، نسخ كل الروابط، ومسح البحث.
- وضع فاتح وداكن.
- SEO عربي، Open Graph، Twitter cards، sitemap، robots، وصورة مشاركة اجتماعية.
- PWA قابلة للتثبيت مع manifest وservice worker بسيط.
- Vercel Analytics مفعلة عبر `@vercel/analytics/react`.

## الهوية والمشاركة الاجتماعية

تم استخدام شعار نطاق الرسمي في:

- شريط التنقل.
- قسم Hero.
- شاشة التحميل.
- التذييل.
- favicon وApple touch icon.
- أيقونات PWA.
- صورة المشاركة الاجتماعية `public/brand/social-preview.png`.

عند مشاركة الرابط على WhatsApp أو X أو Discord أو Telegram أو Facebook أو LinkedIn، تعتمد المعاينة على بيانات Open Graph وTwitter cards في `app/layout.tsx`.

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

تم تثبيت `@vercel/analytics` وإضافة `<Analytics />` مرة واحدة داخل `app/layout.tsx` حتى يعمل عالميًا على كل الصفحات بعد النشر على Vercel. في بيئة التطوير ستظهر رسائل debug فقط ولن تُرسل مشاهدات فعلية، وهذا سلوك طبيعي من Vercel Analytics.

## إعداد رابط الإنتاج للمعاينات

يستخدم المشروع `NEXT_PUBLIC_SITE_URL` إن وُجد لتكوين الروابط المطلقة في metadata. بعد معرفة رابط Vercel النهائي، يمكنك إضافته في إعدادات Vercel:

```text
NEXT_PUBLIC_SITE_URL=https://your-domain.vercel.app
```

إذا لم تضفه، سيستخدم المشروع الرابط الافتراضي:

```text
https://nitaaq-public-search.vercel.app
```

## رفع المشروع إلى GitHub

من داخل مجلد المشروع:

```bash
git init
git add .
git commit -m "Initial nitaaq public search"
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

## متغيرات البيئة

لا يحتاج المشروع إلى مفاتيح سرية أو ملفات `.env` للتشغيل الأساسي. متغير `NEXT_PUBLIC_SITE_URL` اختياري لتحسين دقة روابط المشاركة الاجتماعية بعد النشر.
