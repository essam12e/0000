import { sanitizeInput } from "@/lib/moderation";
import { formatPhoneNumber } from "@/lib/phone";

export type SearchType = "name" | "username" | "email" | "phone";

export type SearchResultLink = {
  id: string;
  title: string;
  description: string;
  url: string;
  accent: string;
};

export type GenerateResult =
  | {
      ok: true;
      query: string;
      displayQuery: string;
      links: SearchResultLink[];
      phoneCountryName?: string;
      phoneCountryDetected?: boolean;
    }
  | {
      ok: false;
      message: string;
    };

const typeLabels: Record<SearchType, string> = {
  name: "اسم",
  username: "اسم مستخدم",
  email: "بريد إلكتروني",
  phone: "رقم جوال"
};

const allowedProtocols = new Set(["https:"]);

function ensureSafeUrl(url: string): string {
  const parsed = new URL(url);
  if (!allowedProtocols.has(parsed.protocol)) {
    throw new Error("Unsafe URL protocol");
  }
  return parsed.toString();
}

function externalSearchUrl(base: string, params: Record<string, string>) {
  const url = new URL(base);
  Object.entries(params).forEach(([key, value]) => {
    url.searchParams.set(key, value);
  });
  return ensureSafeUrl(url.toString());
}

function googleQuery(query: string, suffix?: string) {
  return suffix ? `${query} ${suffix}` : query;
}

function usernameQuery(value: string) {
  return value.replace(/^@+/, "");
}

function validateByType(value: string, type: SearchType): string | null {
  if (!value) {
    return "أدخل قيمة البحث أولًا.";
  }

  if (value.length < 2 && type !== "phone") {
    return "قيمة البحث قصيرة جدًا.";
  }

  if (type === "email" && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/i.test(value)) {
    return "أدخل بريدًا إلكترونيًا صحيحًا.";
  }

  if (type === "username" && !/^@?[a-zA-Z0-9._-]{2,40}$/.test(value)) {
    return "اسم المستخدم يجب أن يحتوي على حروف أو أرقام أو . أو _ أو - فقط.";
  }

  if (type === "name" && !/^[\p{L}\p{M}\s.'-]{2,80}$/u.test(value)) {
    return "استخدم اسمًا واضحًا بدون رموز غير لازمة.";
  }

  return null;
}

function buildCommonLinks(query: string, type: SearchType): SearchResultLink[] {
  const cleanUsername = usernameQuery(query);
  const quoted = type === "email" ? `"${query}"` : query;
  const socialHint = typeLabels[type];

  return [
    {
      id: "google",
      title: "Google Search",
      description: `بحث عام عن ${socialHint}`,
      url: externalSearchUrl("https://www.google.com/search", { q: quoted }),
      accent: "from-emerald-500 to-teal-500"
    },
    {
      id: "images",
      title: "Google Images",
      description: "نتائج الصور العامة",
      url: externalSearchUrl("https://www.google.com/search", {
        q: quoted,
        tbm: "isch"
      }),
      accent: "from-sky-500 to-cyan-500"
    },
    {
      id: "youtube",
      title: "YouTube",
      description: "بحث داخل يوتيوب",
      url: externalSearchUrl("https://www.youtube.com/results", {
        search_query: query
      }),
      accent: "from-red-500 to-rose-500"
    },
    {
      id: "tiktok",
      title: "TikTok",
      description: "بحث عام على تيك توك",
      url: externalSearchUrl("https://www.tiktok.com/search", { q: query }),
      accent: "from-fuchsia-500 to-pink-500"
    },
    {
      id: "x",
      title: "X / Twitter",
      description: "بحث عام على منصة X",
      url: externalSearchUrl("https://x.com/search", {
        q: query,
        src: "typed_query"
      }),
      accent: "from-slate-700 to-slate-950"
    },
    {
      id: "facebook",
      title: "Facebook",
      description: "بحث عام على فيسبوك",
      url: externalSearchUrl("https://www.facebook.com/search/top", {
        q: query
      }),
      accent: "from-blue-500 to-indigo-600"
    },
    {
      id: "instagram",
      title: "Instagram",
      description: "بحث Google داخل صفحات إنستغرام العامة",
      url: externalSearchUrl("https://www.google.com/search", {
        q: googleQuery(query, "site:instagram.com")
      }),
      accent: "from-orange-500 to-pink-600"
    },
    {
      id: "linkedin",
      title: "LinkedIn",
      description: "بحث Google داخل صفحات LinkedIn العامة",
      url: externalSearchUrl("https://www.google.com/search", {
        q: googleQuery(query, "site:linkedin.com/in OR site:linkedin.com/company")
      }),
      accent: "from-blue-600 to-sky-500"
    },
    {
      id: "github",
      title: "GitHub",
      description: "بحث داخل GitHub",
      url: externalSearchUrl("https://github.com/search", {
        q: type === "username" ? cleanUsername : query,
        type: "users"
      }),
      accent: "from-zinc-700 to-zinc-950"
    },
    {
      id: "reddit",
      title: "Reddit",
      description: "بحث عام على Reddit",
      url: externalSearchUrl("https://www.reddit.com/search/", {
        q: query
      }),
      accent: "from-orange-500 to-red-500"
    },
    {
      id: "snapchat",
      title: "Snapchat",
      description: "بحث Google داخل صفحات سناب شات العامة",
      url: externalSearchUrl("https://www.google.com/search", {
        q: googleQuery(query, "site:snapchat.com/add")
      }),
      accent: "from-yellow-300 to-amber-500"
    },
    {
      id: "telegram",
      title: "Telegram",
      description: "بحث Google داخل قنوات وروابط Telegram العامة",
      url: externalSearchUrl("https://www.google.com/search", {
        q: googleQuery(query, "site:t.me")
      }),
      accent: "from-sky-500 to-blue-500"
    }
  ];
}

function buildPhoneLinks(
  phoneQuery: string,
  waNumber: string,
  nationalNumber: string,
  iso2: string
): SearchResultLink[] {
  return [
    ...buildCommonLinks(phoneQuery, "phone").filter((link) =>
      [
        "google",
        "images",
        "youtube",
        "tiktok",
        "x",
        "facebook",
        "telegram"
      ].includes(link.id)
    ),
    {
      id: "whatsapp",
      title: "WhatsApp",
      description: "فتح رابط واتساب مباشر للرقم فقط",
      url: ensureSafeUrl(`https://wa.me/${waNumber}`),
      accent: "from-green-500 to-emerald-600"
    },
    {
      id: "truecaller",
      title: "Truecaller",
      description: "رابط بحث خارجي عام إن كان متاحًا",
      url: ensureSafeUrl(
        `https://www.truecaller.com/search/${iso2.toLowerCase()}/${nationalNumber}`
      ),
      accent: "from-blue-500 to-cyan-500"
    }
  ];
}

export function generateSearchLinks(
  rawValue: string,
  type: SearchType,
  selectedCountryIso2: string
): GenerateResult {
  const sanitized = sanitizeInput(rawValue);
  const validationMessage = validateByType(sanitized, type);

  if (validationMessage) {
    return { ok: false, message: validationMessage };
  }

  if (type === "phone") {
    const phone = formatPhoneNumber(sanitized, selectedCountryIso2);
    if (!phone.ok) {
      return { ok: false, message: phone.message };
    }

    return {
      ok: true,
      query: phone.e164,
      displayQuery: phone.e164,
      phoneCountryName: phone.country.nameAr,
      phoneCountryDetected: phone.detected,
      links: buildPhoneLinks(
        phone.e164,
        phone.waNumber,
        phone.nationalNumber,
        phone.country.iso2
      )
    };
  }

  return {
    ok: true,
    query: sanitized,
    displayQuery: sanitized,
    links: buildCommonLinks(sanitized, type)
  };
}
