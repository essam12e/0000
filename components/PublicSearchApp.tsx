"use client";

import { useEffect, useMemo, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowUpLeft,
  Check,
  ClipboardCopy,
  Eraser,
  ExternalLink,
  Globe2,
  Moon,
  Search,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Sun,
  Unlock,
  Zap
} from "lucide-react";
import { countries } from "@/lib/countries";
import { moderateSearch } from "@/lib/moderation";
import {
  generateSearchLinks,
  SearchResultLink,
  SearchType
} from "@/lib/search";

type Theme = "light" | "dark";

const searchTypes: Array<{
  id: SearchType;
  label: string;
  hint: string;
}> = [
  { id: "name", label: "الاسم", hint: "مثال: أحمد العلي" },
  { id: "username", label: "اسم المستخدم", hint: "مثال: public_user" },
  { id: "email", label: "البريد الإلكتروني", hint: "مثال: name@example.com" },
  { id: "phone", label: "رقم الجوال", hint: "مثال: 05xxxxxxxx أو +9665xxxxxxx" }
];

const safetyPoints = [
  "الموقع مخصص للبحث العام والمصرح فقط.",
  "يمنع استخدام الموقع للتجسس أو التتبع أو المضايقة.",
  "يمنع البحث عن محتوى جنسي أو بذيء أو سياسي متطرف أو مخالف.",
  "استخدم الموقع فقط لحساباتك أو لأبحاث تجارية أو لأغراض مصرح بها.",
  "الموقع لا يخزن عمليات البحث الخاصة بك."
];

const faqItems = [
  {
    question: "هل يجمع الموقع بيانات البحث؟",
    answer:
      "لا. يتم إنشاء الروابط داخل المتصفح فقط، ولا يتم إرسال قيمة البحث إلى خادم خاص بالموقع."
  },
  {
    question: "هل يستطيع الموقع الوصول إلى حسابات خاصة؟",
    answer:
      "لا. الأداة لا تخترق الحسابات ولا تتجاوز تسجيل الدخول، بل تفتح صفحات بحث عامة على منصات خارجية."
  },
  {
    question: "لماذا توجد فلترة للمحتوى؟",
    answer:
      "لمنع الاستخدام المسيء مثل المضايقة، الدوكسنغ، المحتوى الجنسي، التطرف، أو أي نية ضارة."
  },
  {
    question: "هل روابط الجوال مضمونة النتائج؟",
    answer:
      "الروابط تفتح صفحات عامة فقط. ظهور النتائج يعتمد على كل منصة وسياساتها وتوفر المحتوى العام."
  }
];

const fadeUp = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0 }
};

function ResultSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 6 }).map((_, index) => (
        <div
          className="h-36 animate-pulse rounded-3xl border border-white/50 bg-white/50 dark:border-white/10 dark:bg-white/10"
          key={index}
        />
      ))}
    </div>
  );
}

function ResultCard({ link, index }: { link: SearchResultLink; index: number }) {
  return (
    <motion.a
      animate={{ opacity: 1, y: 0 }}
      className="group relative overflow-hidden rounded-3xl border border-white/60 bg-white/78 p-5 shadow-soft transition duration-300 hover:-translate-y-1 hover:shadow-glow focus-ring dark:border-white/10 dark:bg-white/10"
      href={link.url}
      initial={{ opacity: 0, y: 20 }}
      rel="noopener noreferrer"
      target="_blank"
      transition={{ delay: index * 0.035, duration: 0.35 }}
    >
      <div
        className={`absolute inset-x-0 top-0 h-1 bg-gradient-to-l ${link.accent}`}
      />
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-slate-950 dark:text-white">
            {link.title}
          </h3>
          <p className="mt-2 text-sm leading-7 text-slate-600 dark:text-slate-300">
            {link.description}
          </p>
        </div>
        <span className="grid size-10 shrink-0 place-items-center rounded-2xl bg-slate-950 text-white transition group-hover:rotate-[-10deg] dark:bg-white dark:text-slate-950">
          <ExternalLink className="size-4" />
        </span>
      </div>
      <p className="mt-5 truncate rounded-2xl bg-slate-100 px-3 py-2 text-xs text-slate-500 dark:bg-white/10 dark:text-slate-400">
        {link.url}
      </p>
    </motion.a>
  );
}

export function PublicSearchApp() {
  const [theme, setTheme] = useState<Theme>("light");
  const [query, setQuery] = useState("");
  const [type, setType] = useState<SearchType>("name");
  const [selectedCountry, setSelectedCountry] = useState("SA");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [results, setResults] = useState<SearchResultLink[]>([]);
  const [displayQuery, setDisplayQuery] = useState("");
  const [countryMessage, setCountryMessage] = useState("");

  const currentType = useMemo(
    () => searchTypes.find((item) => item.id === type) ?? searchTypes[0],
    [type]
  );

  useEffect(() => {
    const stored = window.localStorage.getItem("psc-theme");
    const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
    const nextTheme = stored === "dark" || (!stored && prefersDark) ? "dark" : "light";
    setTheme(nextTheme);
  }, []);

  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    window.localStorage.setItem("psc-theme", theme);
  }, [theme]);

  function handleGenerate() {
    setError("");
    setSuccess("");
    setCountryMessage("");
    setResults([]);
    setDisplayQuery("");

    const moderation = moderateSearch(query);
    if (!moderation.allowed) {
      setError(moderation.message);
      return;
    }

    setIsLoading(true);
    window.setTimeout(() => {
      const generated = generateSearchLinks(query, type, selectedCountry);
      setIsLoading(false);

      if (!generated.ok) {
        setError(generated.message);
        return;
      }

      setResults(generated.links);
      setDisplayQuery(generated.displayQuery);
      setSuccess(`تم إنشاء ${generated.links.length} رابط بحث عام بشكل آمن.`);

      if (generated.phoneCountryName) {
        setCountryMessage(
          generated.phoneCountryDetected
            ? `تم اكتشاف الدولة تلقائيًا: ${generated.phoneCountryName}`
            : `سيتم استخدام رمز الدولة المحدد: ${generated.phoneCountryName}`
        );
      }
    }, 420);
  }

  function openAllResults() {
    results.forEach((result, index) => {
      window.setTimeout(() => {
        window.open(result.url, "_blank", "noopener,noreferrer");
      }, index * 120);
    });
  }

  async function copyAllLinks() {
    if (!results.length) {
      return;
    }

    await navigator.clipboard.writeText(
      results.map((result) => `${result.title}: ${result.url}`).join("\n")
    );
    setSuccess("تم نسخ كل الروابط إلى الحافظة.");
  }

  function clearSearch() {
    setQuery("");
    setResults([]);
    setDisplayQuery("");
    setError("");
    setSuccess("");
    setCountryMessage("");
  }

  return (
    <main className="min-h-screen overflow-hidden">
      <nav className="mx-auto flex w-full max-w-7xl items-center justify-between px-5 py-5 sm:px-8">
        <a className="flex items-center gap-3 focus-ring" href="#top">
          <span className="grid size-11 place-items-center rounded-2xl bg-slate-950 text-white shadow-glow dark:bg-white dark:text-slate-950">
            <ShieldCheck className="size-5" />
          </span>
          <span>
            <span className="block text-sm font-black text-slate-950 dark:text-white">
              Public Search Center
            </span>
            <span className="block text-xs text-slate-500 dark:text-slate-400">
              بحث عام يحترم الخصوصية
            </span>
          </span>
        </a>

        <button
          aria-label="تبديل الوضع الداكن"
          className="grid size-11 place-items-center rounded-2xl border border-slate-200 bg-white/70 text-slate-800 shadow-sm transition hover:-translate-y-0.5 focus-ring dark:border-white/10 dark:bg-white/10 dark:text-white"
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          type="button"
        >
          {theme === "dark" ? <Sun className="size-5" /> : <Moon className="size-5" />}
        </button>
      </nav>

      <section
        className="mx-auto grid min-h-[82vh] w-full max-w-7xl items-center gap-10 px-5 pb-16 pt-8 sm:px-8 lg:grid-cols-[1.02fr_0.98fr]"
        id="top"
      >
        <motion.div
          animate="visible"
          className="relative z-10"
          initial="hidden"
          transition={{ staggerChildren: 0.08 }}
        >
          <motion.div
            className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-semibold text-emerald-800 dark:border-emerald-400/20 dark:bg-emerald-400/10 dark:text-emerald-200"
            variants={fadeUp}
          >
            <Sparkles className="size-4" />
            أداة بحث عامة بدون تخزين بياناتك
          </motion.div>
          <motion.h1
            className="mt-7 max-w-3xl text-5xl font-black leading-[1.12] tracking-normal text-slate-950 dark:text-white sm:text-6xl lg:text-7xl"
            variants={fadeUp}
          >
            مركز البحث العام الذكي
          </motion.h1>
          <motion.p
            className="mt-6 max-w-2xl text-lg leading-9 text-slate-700 dark:text-slate-300 sm:text-xl"
            variants={fadeUp}
          >
            أداة عربية تساعدك على فتح نتائج البحث العامة من عدة منصات بسرعة،
            بطريقة آمنة ومنظمة وبدون تخزين بياناتك.
          </motion.p>
          <motion.div
            className="mt-8 flex flex-wrap items-center gap-3"
            variants={fadeUp}
          >
            <a
              className="inline-flex items-center gap-2 rounded-2xl bg-slate-950 px-6 py-4 text-sm font-bold text-white shadow-glow transition hover:-translate-y-1 focus-ring dark:bg-white dark:text-slate-950"
              href="#search"
            >
              <Search className="size-5" />
              ابدأ البحث الآن
            </a>
            <a
              className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white/70 px-6 py-4 text-sm font-bold text-slate-800 shadow-sm transition hover:-translate-y-1 focus-ring dark:border-white/10 dark:bg-white/10 dark:text-white"
              href="#policy"
            >
              <ShieldCheck className="size-5" />
              سياسة الاستخدام الآمن
            </a>
          </motion.div>
          <motion.p
            className="mt-7 max-w-xl rounded-3xl border border-amber-200 bg-amber-50/80 p-4 text-sm leading-7 text-amber-900 dark:border-amber-300/20 dark:bg-amber-300/10 dark:text-amber-100"
            variants={fadeUp}
          >
            هذه الأداة لا تجمع البيانات ولا تخترق الحسابات. هي فقط تنشئ روابط
            بحث عامة على منصات متاحة للجميع.
          </motion.p>
        </motion.div>

        <motion.div
          animate={{ opacity: 1, scale: 1 }}
          className="glass-panel relative rounded-[2rem] p-5 sm:p-7"
          initial={{ opacity: 0, scale: 0.96 }}
          transition={{ duration: 0.6 }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            {[
              ["خصوصية", "لا تخزين للبحث", ShieldCheck],
              ["سرعة", "روابط جاهزة فورًا", Zap],
              ["منصات", "نتائج عامة متعددة", Globe2],
              ["جوال", "اكتشاف رمز الدولة", Smartphone]
            ].map(([title, text, Icon]) => (
              <div
                className="rounded-3xl border border-white/60 bg-white/70 p-5 dark:border-white/10 dark:bg-white/10"
                key={String(title)}
              >
                <Icon className="size-6 text-emerald-700 dark:text-emerald-300" />
                <h2 className="mt-4 text-lg font-black text-slate-950 dark:text-white">
                  {String(title)}
                </h2>
                <p className="mt-2 text-sm leading-7 text-slate-600 dark:text-slate-300">
                  {String(text)}
                </p>
              </div>
            ))}
          </div>
        </motion.div>
      </section>

      <section className="px-5 py-12 sm:px-8" id="search">
        <div className="mx-auto max-w-7xl">
          <div className="grid gap-6 lg:grid-cols-[0.92fr_1.08fr]">
            <motion.div
              className="glass-panel rounded-[2rem] p-5 sm:p-7"
              initial={{ opacity: 0, y: 30 }}
              transition={{ duration: 0.5 }}
              viewport={{ once: true, margin: "-80px" }}
              whileInView={{ opacity: 1, y: 0 }}
            >
              <div className="flex items-center gap-3">
                <span className="grid size-11 place-items-center rounded-2xl bg-emerald-600 text-white">
                  <Search className="size-5" />
                </span>
                <div>
                  <h2 className="text-2xl font-black text-slate-950 dark:text-white">
                    قسم البحث
                  </h2>
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    كل شيء يحدث داخل المتصفح فقط.
                  </p>
                </div>
              </div>

              <div className="mt-7 grid grid-cols-2 gap-2 rounded-3xl bg-slate-100 p-2 dark:bg-white/10">
                {searchTypes.map((item) => (
                  <button
                    className={`rounded-2xl px-3 py-3 text-sm font-bold transition focus-ring ${
                      type === item.id
                        ? "bg-white text-slate-950 shadow-sm dark:bg-slate-950 dark:text-white"
                        : "text-slate-600 hover:bg-white/60 dark:text-slate-300 dark:hover:bg-white/10"
                    }`}
                    key={item.id}
                    onClick={() => {
                      setType(item.id);
                      setError("");
                      setSuccess("");
                      setResults([]);
                      setDisplayQuery("");
                      setCountryMessage("");
                    }}
                    type="button"
                  >
                    {item.label}
                  </button>
                ))}
              </div>

              <label className="mt-6 block text-sm font-bold text-slate-800 dark:text-slate-100">
                قيمة البحث
              </label>
              <input
                className="focus-ring mt-3 w-full rounded-3xl border border-slate-200 bg-white/80 px-5 py-4 text-base text-slate-950 shadow-sm transition placeholder:text-slate-400 dark:border-white/10 dark:bg-white/10 dark:text-white"
                inputMode={type === "phone" ? "tel" : type === "email" ? "email" : "text"}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setError("");
                  setSuccess("");
                  setResults([]);
                  setDisplayQuery("");
                  setCountryMessage("");
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    handleGenerate();
                  }
                }}
                placeholder={currentType.hint}
                value={query}
              />

              {type === "phone" ? (
                <div className="mt-5">
                  <label className="block text-sm font-bold text-slate-800 dark:text-slate-100">
                    رمز الدولة عند إدخال رقم محلي
                  </label>
                  <select
                    className="focus-ring mt-3 w-full rounded-3xl border border-slate-200 bg-white/80 px-5 py-4 text-base text-slate-950 shadow-sm dark:border-white/10 dark:bg-slate-950 dark:text-white"
                    onChange={(event) => setSelectedCountry(event.target.value)}
                    value={selectedCountry}
                  >
                    {countries.map((country) => (
                      <option key={`${country.iso2}-${country.dialCode}`} value={country.iso2}>
                        {country.nameAr} (+{country.dialCode})
                      </option>
                    ))}
                  </select>
                </div>
              ) : null}

              {error ? (
                <p className="mt-5 rounded-3xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold leading-7 text-red-700 dark:border-red-400/20 dark:bg-red-400/10 dark:text-red-200">
                  {error}
                </p>
              ) : null}

              {success ? (
                <p className="mt-5 rounded-3xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold leading-7 text-emerald-800 dark:border-emerald-400/20 dark:bg-emerald-400/10 dark:text-emerald-200">
                  {success}
                </p>
              ) : null}

              {countryMessage ? (
                <p className="mt-3 rounded-3xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm font-semibold leading-7 text-sky-800 dark:border-sky-400/20 dark:bg-sky-400/10 dark:text-sky-200">
                  {countryMessage}
                </p>
              ) : null}

              <div className="mt-6 grid gap-3 sm:grid-cols-3">
                <button
                  className="inline-flex items-center justify-center gap-2 rounded-2xl bg-slate-950 px-4 py-4 text-sm font-black text-white shadow-glow transition hover:-translate-y-1 focus-ring disabled:cursor-not-allowed disabled:opacity-60 dark:bg-white dark:text-slate-950"
                  disabled={isLoading}
                  onClick={handleGenerate}
                  type="button"
                >
                  <Search className="size-5" />
                  إنشاء الروابط
                </button>
                <button
                  className="inline-flex items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-white/70 px-4 py-4 text-sm font-bold text-slate-800 transition hover:-translate-y-1 focus-ring disabled:cursor-not-allowed disabled:opacity-50 dark:border-white/10 dark:bg-white/10 dark:text-white"
                  disabled={!results.length}
                  onClick={copyAllLinks}
                  type="button"
                >
                  <ClipboardCopy className="size-5" />
                  نسخ كل الروابط
                </button>
                <button
                  className="inline-flex items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-white/70 px-4 py-4 text-sm font-bold text-slate-800 transition hover:-translate-y-1 focus-ring dark:border-white/10 dark:bg-white/10 dark:text-white"
                  onClick={clearSearch}
                  type="button"
                >
                  <Eraser className="size-5" />
                  مسح البحث
                </button>
              </div>
            </motion.div>

            <motion.div
              className="glass-panel rounded-[2rem] p-5 sm:p-7"
              id="results"
              initial={{ opacity: 0, y: 30 }}
              transition={{ duration: 0.5, delay: 0.1 }}
              viewport={{ once: true, margin: "-80px" }}
              whileInView={{ opacity: 1, y: 0 }}
            >
              <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                <div>
                  <h2 className="text-2xl font-black text-slate-950 dark:text-white">
                    قسم النتائج
                  </h2>
                  <p className="mt-1 text-sm leading-7 text-slate-500 dark:text-slate-400">
                    روابط خارجية آمنة تفتح في تبويبات جديدة.
                  </p>
                </div>
                <button
                  className="inline-flex items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-5 py-3 text-sm font-black text-white shadow-glow transition hover:-translate-y-1 focus-ring disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={!results.length}
                  onClick={openAllResults}
                  type="button"
                >
                  <ArrowUpLeft className="size-5" />
                  فتح كل النتائج
                </button>
              </div>

              {displayQuery ? (
                <div className="mt-5 flex flex-wrap items-center gap-2 text-sm">
                  <span className="rounded-full bg-slate-950 px-4 py-2 font-bold text-white dark:bg-white dark:text-slate-950">
                    البحث الحالي
                  </span>
                  <span className="rounded-full border border-slate-200 bg-white/70 px-4 py-2 font-semibold text-slate-700 dark:border-white/10 dark:bg-white/10 dark:text-slate-200">
                    {displayQuery}
                  </span>
                </div>
              ) : null}

              <div className="mt-6">
                {isLoading ? <ResultSkeleton /> : null}

                {!isLoading && !results.length ? (
                  <div className="rounded-[2rem] border border-dashed border-slate-300 bg-white/55 p-8 text-center dark:border-white/15 dark:bg-white/5">
                    <Unlock className="mx-auto size-10 text-emerald-700 dark:text-emerald-300" />
                    <h3 className="mt-4 text-xl font-black text-slate-950 dark:text-white">
                      لا توجد نتائج بعد
                    </h3>
                    <p className="mx-auto mt-3 max-w-md text-sm leading-7 text-slate-600 dark:text-slate-300">
                      اختر نوع البحث وأدخل قيمة آمنة، ثم أنشئ الروابط العامة
                      بدون تخزين أو إرسال بياناتك للموقع.
                    </p>
                  </div>
                ) : null}

                {!isLoading && results.length ? (
                  <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                    {results.map((link, index) => (
                      <ResultCard index={index} key={link.id} link={link} />
                    ))}
                  </div>
                ) : null}
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      <section className="px-5 py-14 sm:px-8" id="policy">
        <motion.div
          className="mx-auto grid max-w-7xl gap-6 lg:grid-cols-[0.85fr_1.15fr]"
          initial={{ opacity: 0, y: 24 }}
          transition={{ duration: 0.5 }}
          viewport={{ once: true }}
          whileInView={{ opacity: 1, y: 0 }}
        >
          <div>
            <span className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-bold text-emerald-800 dark:border-emerald-400/20 dark:bg-emerald-400/10 dark:text-emerald-200">
              <ShieldCheck className="size-4" />
              سياسة الاستخدام الآمن
            </span>
            <h2 className="mt-5 text-4xl font-black leading-tight text-slate-950 dark:text-white">
              استخدام عام ومصرح، بدون مراقبة أو إساءة.
            </h2>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {safetyPoints.map((point) => (
              <div
                className="rounded-3xl border border-white/60 bg-white/72 p-5 shadow-soft dark:border-white/10 dark:bg-white/10"
                key={point}
              >
                <Check className="size-5 text-emerald-700 dark:text-emerald-300" />
                <p className="mt-3 text-sm font-semibold leading-7 text-slate-700 dark:text-slate-200">
                  {point}
                </p>
              </div>
            ))}
          </div>
        </motion.div>
      </section>

      <section className="px-5 py-14 sm:px-8" id="faq">
        <div className="mx-auto max-w-5xl">
          <div className="text-center">
            <h2 className="text-4xl font-black text-slate-950 dark:text-white">
              الأسئلة الشائعة
            </h2>
            <p className="mx-auto mt-4 max-w-2xl text-base leading-8 text-slate-600 dark:text-slate-300">
              إجابات مختصرة عن الخصوصية، حدود الأداة، وطريقة عمل الروابط العامة.
            </p>
          </div>
          <div className="mt-8 grid gap-4">
            {faqItems.map((item, index) => (
              <motion.details
                className="group rounded-3xl border border-white/60 bg-white/72 p-5 shadow-soft open:bg-white dark:border-white/10 dark:bg-white/10 dark:open:bg-white/15"
                initial={{ opacity: 0, y: 18 }}
                key={item.question}
                transition={{ delay: index * 0.06, duration: 0.35 }}
                viewport={{ once: true }}
                whileInView={{ opacity: 1, y: 0 }}
              >
                <summary className="cursor-pointer list-none text-lg font-black text-slate-950 focus-ring dark:text-white">
                  {item.question}
                </summary>
                <p className="mt-3 text-sm leading-8 text-slate-600 dark:text-slate-300">
                  {item.answer}
                </p>
              </motion.details>
            ))}
          </div>
        </div>
      </section>

      <footer className="px-5 py-8 sm:px-8">
        <div className="mx-auto flex max-w-7xl flex-col justify-between gap-4 border-t border-slate-200/70 pt-8 text-sm text-slate-500 dark:border-white/10 dark:text-slate-400 sm:flex-row sm:items-center">
          <p>© 2026 Public Search Center. جميع الحقوق محفوظة.</p>
          <p>مصمم كأداة بحث عامة آمنة لا تخزن عمليات البحث.</p>
        </div>
      </footer>
    </main>
  );
}
