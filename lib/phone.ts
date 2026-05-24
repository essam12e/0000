import {
  CountryCode,
  detectCountryFromInternationalNumber,
  getCountryByIso
} from "@/lib/countries";

export type PhoneFormatResult =
  | {
      ok: true;
      e164: string;
      waNumber: string;
      nationalNumber: string;
      country: CountryCode;
      detected: boolean;
    }
  | {
      ok: false;
      message: string;
      detectedCountry?: CountryCode | null;
    };

export function formatPhoneNumber(
  rawValue: string,
  selectedIso2: string
): PhoneFormatResult {
  const trimmed = rawValue.trim();
  const hasInternationalPrefix = /^\s*\+/.test(trimmed);
  const digits = trimmed.replace(/\D/g, "");

  if (digits.length < 6) {
    return {
      ok: false,
      message: "أدخل رقم جوال صحيحًا يحتوي على 6 أرقام على الأقل."
    };
  }

  if (hasInternationalPrefix) {
    const detectedCountry = detectCountryFromInternationalNumber(trimmed);
    if (!detectedCountry) {
      return {
        ok: false,
        message: "لم نتمكن من تحديد رمز الدولة من الرقم الدولي.",
        detectedCountry: null
      };
    }

    const nationalNumber = digits.slice(detectedCountry.dialCode.length);
    const waNumber = `${detectedCountry.dialCode}${nationalNumber}`;

    if (waNumber.length > 15 || nationalNumber.length < 4) {
      return {
        ok: false,
        message: "صيغة الرقم الدولي غير صحيحة أو طويلة جدًا.",
        detectedCountry
      };
    }

    return {
      ok: true,
      e164: `+${waNumber}`,
      waNumber,
      nationalNumber,
      country: detectedCountry,
      detected: true
    };
  }

  const selectedCountry = getCountryByIso(selectedIso2);
  const nationalNumber = digits.replace(/^0+/, "");
  const waNumber = `${selectedCountry.dialCode}${nationalNumber}`;

  if (waNumber.length > 15 || nationalNumber.length < 4) {
    return {
      ok: false,
      message: "تحقق من الرقم المحلي ورمز الدولة قبل إنشاء الروابط.",
      detectedCountry: selectedCountry
    };
  }

  return {
    ok: true,
    e164: `+${waNumber}`,
    waNumber,
    nationalNumber,
    country: selectedCountry,
    detected: false
  };
}
