export type ModerationResult =
  | { allowed: true }
  | { allowed: false; message: string };

const blockedTerms = [
  "porn",
  "porno",
  "xxx",
  "sex",
  "nude",
  "nudes",
  "escort",
  "onlyfans",
  "adult",
  "rape",
  "kill",
  "murder",
  "bomb",
  "terror",
  "terrorism",
  "isis",
  "daesh",
  "alqaeda",
  "weapon",
  "gun",
  "rifle",
  "drugs",
  "cocaine",
  "heroin",
  "meth",
  "hack",
  "dox",
  "doxx",
  "doxxing",
  "stalk",
  "tracking",
  "minor",
  "child porn",
  "cp",
  "كلمات اباحية",
  "اباحي",
  "إباحي",
  "جنس",
  "جنسي",
  "عاري",
  "عارية",
  "صور عارية",
  "دعارة",
  "قذف",
  "شتيمة",
  "سب",
  "لعن",
  "تهديد",
  "اقتل",
  "قتل",
  "تفجير",
  "قنبلة",
  "ارهاب",
  "إرهاب",
  "داعش",
  "القاعدة",
  "تطرف",
  "متطرف",
  "سلاح",
  "أسلحة",
  "مسدس",
  "مخدرات",
  "كوكايين",
  "هيروين",
  "اختراق",
  "تهكير",
  "هاك",
  "تجسس",
  "تتبع",
  "ملاحقة",
  "ابتزاز",
  "دوكس",
  "تسريب بيانات",
  "بيانات خاصة",
  "قاصر",
  "قاصرين",
  "اطفال",
  "أطفال"
];

const blockedPatterns = [
  /\bhow\s+to\s+(hack|dox|track|stalk)\b/i,
  /\b(find|get|leak)\s+(address|password|private|location)\b/i,
  /كيف\s+(اخترق|أخترق|اتجسس|أتجسس|اتتبع|أتتبع)/i,
  /(عنوان|كلمة مرور|موقع|بيانات)\s+(شخص|خاص|خاصة)/i
];

export const safeUseMessage =
  "لا يمكن تنفيذ هذا البحث لأنه يخالف سياسة الاستخدام الآمن للموقع.";

export function sanitizeInput(value: string): string {
  return value
    .normalize("NFKC")
    .replace(/[\u0000-\u001f\u007f<>`{}[\]\\|^~]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120);
}

export function moderateSearch(value: string): ModerationResult {
  const normalized = sanitizeInput(value).toLowerCase();
  if (!normalized) {
    return { allowed: true };
  }

  const hasBlockedTerm = blockedTerms.some((term) =>
    normalized.includes(term.toLowerCase())
  );
  const hasBlockedPattern = blockedPatterns.some((pattern) =>
    pattern.test(normalized)
  );

  if (hasBlockedTerm || hasBlockedPattern) {
    return { allowed: false, message: safeUseMessage };
  }

  return { allowed: true };
}
