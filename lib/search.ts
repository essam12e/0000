import { sanitizeInput } from "@/lib/moderation";
import type { PlatformId } from "@/lib/open-platform-link";
import { formatPhoneNumber } from "@/lib/phone";

export type SearchType = "name" | "username" | "email" | "phone";

export type SearchResultLink = {
  id: string;
  platform: PlatformId;
  title: string;
  description: string;
  url: string;
  appUrl?: string;
  intentUrl?: string;
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

type RawSearchResultLink = Omit<
  SearchResultLink,
  "platform" | "appUrl" | "intentUrl"
> & {
  platform?: PlatformId;
};

function androidIntentUrl(webUrl: string, packageName: string): string {
  const parsed = new URL(webUrl);
  return `intent://${parsed.host}${parsed.pathname}${parsed.search}#Intent;scheme=${parsed.protocol.replace(
    ":",
    ""
  )};package=${packageName};S.browser_fallback_url=${encodeURIComponent(
    webUrl
  )};end`;
}

function androidAppIntentUrl(
  appPath: string,
  scheme: string,
  packageName: string,
  webUrl: string
): string {
  return `intent://${appPath}#Intent;scheme=${scheme};package=${packageName};S.browser_fallback_url=${encodeURIComponent(
    webUrl
  )};end`;
}

function platformLinks(
  platform: PlatformId,
  webUrl: string,
  query: string,
  cleanUsername: string,
  type: SearchType
): Pick<SearchResultLink, "appUrl" | "intentUrl"> {
  const encodedQuery = encodeURIComponent(query);
  const encodedUsername = encodeURIComponent(cleanUsername);

  switch (platform) {
    case "youtube":
      return {
        appUrl: `youtube://www.youtube.com/results?search_query=${encodedQuery}`,
        intentUrl: androidIntentUrl(webUrl, "com.google.android.youtube")
      };
    case "tiktok":
      return {
        appUrl: `snssdk1233://search?keyword=${encodedQuery}`,
        intentUrl: androidIntentUrl(webUrl, "com.zhiliaoapp.musically")
      };
    case "x":
      return {
        appUrl: `twitter://search?query=${encodedQuery}`,
        intentUrl: androidIntentUrl(webUrl, "com.twitter.android")
      };
    case "facebook":
      return {
        appUrl: `fb://search/top/?q=${encodedQuery}`,
        intentUrl: androidAppIntentUrl(
          `search/top/?q=${encodedQuery}`,
          "fb",
          "com.facebook.katana",
          webUrl
        )
      };
    case "instagram":
      return {
        appUrl:
          type === "username"
            ? `instagram://user?username=${encodedUsername}`
            : `instagram://search?query=${encodedQuery}`,
        intentUrl: androidAppIntentUrl(
          type === "username"
            ? `user?username=${encodedUsername}`
            : `search?query=${encodedQuery}`,
          "instagram",
          "com.instagram.android",
          webUrl
        )
      };
    case "linkedin":
      return {
        appUrl: `linkedin://search/results/all?keywords=${encodedQuery}`,
        intentUrl: androidAppIntentUrl(
          `search/results/all?keywords=${encodedQuery}`,
          "linkedin",
          "com.linkedin.android",
          webUrl
        )
      };
    case "github":
      return {
        appUrl: `github://search?q=${encodedQuery}`,
        intentUrl: androidIntentUrl(webUrl, "com.github.android")
      };
    case "reddit":
      return {
        appUrl: `reddit://search?q=${encodedQuery}`,
        intentUrl: androidIntentUrl(webUrl, "com.reddit.frontpage")
      };
    case "snapchat":
      return {
        appUrl:
          type === "username"
            ? `snapchat://add/${encodedUsername}`
            : `snapchat://search?query=${encodedQuery}`,
        intentUrl: androidAppIntentUrl(
          type === "username"
            ? `add/${encodedUsername}`
            : `search?query=${encodedQuery}`,
          "snapchat",
          "com.snapchat.android",
          webUrl
        )
      };
    case "telegram":
      return {
        appUrl:
          type === "username"
            ? `tg://resolve?domain=${encodedUsername}`
            : `tg://search?query=${encodedQuery}`,
        intentUrl: androidAppIntentUrl(
          type === "username"
            ? `resolve?domain=${encodedUsername}`
            : `search?query=${encodedQuery}`,
          "tg",
          "org.telegram.messenger",
          webUrl
        )
      };
    default:
      return {};
  }
}

function enrichLink(
  link: RawSearchResultLink,
  query: string,
  cleanUsername: string,
  type: SearchType
): SearchResultLink {
  const platform = link.platform ?? (link.id as PlatformId);

  return {
    ...link,
    platform,
    ...platformLinks(platform, link.url, query, cleanUsername, type)
  };
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

  const links: RawSearchResultLink[] = [
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

  return links.map((link) => enrichLink(link, query, cleanUsername, type));
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
      platform: "whatsapp",
      title: "WhatsApp",
      description: "فتح رابط واتساب مباشر للرقم فقط",
      url: ensureSafeUrl(`https://wa.me/${waNumber}`),
      appUrl: `whatsapp://send?phone=${waNumber}`,
      intentUrl: `intent://send?phone=${waNumber}#Intent;scheme=whatsapp;package=com.whatsapp;S.browser_fallback_url=${encodeURIComponent(
        `https://wa.me/${waNumber}`
      )};end`,
      accent: "from-green-500 to-emerald-600"
    },
    {
      id: "truecaller",
      platform: "truecaller",
      title: "Truecaller",
      description: "رابط بحث خارجي عام إن كان متاحًا",
      url: ensureSafeUrl(
        `https://www.truecaller.com/search/${iso2.toLowerCase()}/${nationalNumber}`
      ),
      appUrl: `truecaller://search?number=${waNumber}`,
      intentUrl: androidIntentUrl(
        `https://www.truecaller.com/search/${iso2.toLowerCase()}/${nationalNumber}`,
        "com.truecaller"
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
