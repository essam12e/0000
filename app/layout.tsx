import type { Metadata, Viewport } from "next";
import { Analytics } from "@vercel/analytics/react";
import "./globals.css";
import { PwaRegister } from "@/components/PwaRegister";

const siteTitle = "مركز البحث العام الذكي | أداة بحث عربية آمنة";
const siteDescription =
  "موقع عربي يساعدك على إنشاء روابط بحث عامة وآمنة عبر Google وYouTube وTikTok وX ومنصات أخرى بدون تخزين بياناتك.";

export const metadata: Metadata = {
  metadataBase: new URL("https://public-search-center.vercel.app"),
  title: {
    default: siteTitle,
    template: "%s | مركز البحث العام الذكي"
  },
  description: siteDescription,
  applicationName: "Public Search Center",
  manifest: "/manifest.json",
  icons: {
    icon: [
      { url: "/favicon.ico" },
      { url: "/icon-192.svg", type: "image/svg+xml", sizes: "192x192" }
    ],
    apple: [{ url: "/icon-192.svg", type: "image/svg+xml" }]
  },
  keywords: [
    "بحث عام",
    "أداة بحث عربية",
    "روابط بحث آمنة",
    "بحث Google",
    "بحث منصات اجتماعية",
    "خصوصية"
  ],
  authors: [{ name: "Public Search Center" }],
  creator: "Public Search Center",
  publisher: "Public Search Center",
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-snippet": -1,
      "max-image-preview": "large",
      "max-video-preview": -1
    }
  },
  openGraph: {
    type: "website",
    locale: "ar_SA",
    url: "https://public-search-center.vercel.app",
    siteName: "Public Search Center",
    title: siteTitle,
    description: siteDescription,
    images: [
      {
        url: "/og.svg",
        width: 1200,
        height: 630,
        alt: "مركز البحث العام الذكي"
      }
    ]
  },
  twitter: {
    card: "summary_large_image",
    title: siteTitle,
    description: siteDescription,
    images: ["/og.svg"]
  },
  appleWebApp: {
    capable: true,
    title: "مركز البحث العام",
    statusBarStyle: "black-translucent"
  },
  formatDetection: {
    telephone: false,
    email: false,
    address: false
  }
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f7f3ec" },
    { media: "(prefers-color-scheme: dark)", color: "#08111f" }
  ]
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ar" dir="rtl" suppressHydrationWarning>
      <body>
        {children}
        <PwaRegister />
        <Analytics />
      </body>
    </html>
  );
}
