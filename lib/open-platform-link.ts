export type PlatformId =
  | "google"
  | "images"
  | "youtube"
  | "tiktok"
  | "x"
  | "facebook"
  | "instagram"
  | "linkedin"
  | "github"
  | "reddit"
  | "snapchat"
  | "telegram"
  | "whatsapp"
  | "truecaller";

type DeviceKind = "android" | "ios" | "desktop";

const allowedWebProtocols = new Set(["https:"]);
const allowedAppProtocols = new Set([
  "fb:",
  "github:",
  "instagram:",
  "linkedin:",
  "reddit:",
  "sc-corp:",
  "snapchat:",
  "snssdk1233:",
  "tg:",
  "tiktok:",
  "twitter:",
  "truecaller:",
  "vnd.youtube:",
  "whatsapp:",
  "youtube:"
]);

function detectDevice(): DeviceKind {
  if (typeof navigator === "undefined") {
    return "desktop";
  }

  const userAgent = navigator.userAgent || "";
  const platform = navigator.platform || "";
  const maxTouchPoints = navigator.maxTouchPoints || 0;
  const isAndroid = /Android/i.test(userAgent);
  const isIOS =
    /iPhone|iPad|iPod/i.test(userAgent) ||
    (platform === "MacIntel" && maxTouchPoints > 1);

  if (isAndroid) {
    return "android";
  }

  if (isIOS) {
    return "ios";
  }

  return "desktop";
}

function isSafeWebUrl(url: string): boolean {
  try {
    return allowedWebProtocols.has(new URL(url).protocol);
  } catch {
    return false;
  }
}

function isSafeAppUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    return (
      allowedAppProtocols.has(parsed.protocol) ||
      (parsed.protocol === "intent:" && url.includes("#Intent;"))
    );
  } catch {
    return false;
  }
}

function openBlank(url: string): Window | null {
  return window.open(url, "_blank", "noopener,noreferrer");
}

export function openPlatformLink(
  platform: PlatformId,
  webUrl: string,
  appUrl?: string,
  intentUrl?: string
) {
  void platform;

  if (typeof window === "undefined" || !isSafeWebUrl(webUrl)) {
    return;
  }

  const device = detectDevice();

  if (device === "desktop") {
    openBlank(webUrl);
    return;
  }

  if (device === "android" && intentUrl && isSafeAppUrl(intentUrl)) {
    const opened = openBlank(intentUrl);
    if (!opened) {
      openBlank(webUrl);
    }
    return;
  }

  if (device === "ios" && appUrl && isSafeAppUrl(appUrl)) {
    const startedAt = Date.now();
    const opened = openBlank(appUrl);

    window.setTimeout(() => {
      const likelyStayedOnPage =
        document.visibilityState === "visible" && Date.now() - startedAt < 2500;

      if (!opened || likelyStayedOnPage) {
        openBlank(webUrl);
      }
    }, 900);
    return;
  }

  openBlank(webUrl);
}
