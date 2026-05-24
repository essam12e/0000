import type { Metadata, Viewport } from "next";
import { Analytics } from "@vercel/analytics/react";
import "./globals.css";
import { PwaRegister } from "@/components/PwaRegister";

const siteUrl =
  process.env.NEXT_PUBLIC_SITE_URL ?? "https://nitaaq-public-search.vercel.app";
const siteTitle = "نطاق | الباحث العام";
const siteDescription =
  "منصة بحث عامة ذكية وآمنة تساعدك على فتح نتائج البحث العامة عبر عدة منصات بطريقة احترافية ومنظمة.";
const socialImage = "/brand/social-preview.png";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: siteTitle,
    template: `%s | ${siteTitle}`
  },
  description: siteDescription,
  applicationName: siteTitle,
  manifest: "/manifest.json",
  alternates: {
    canonical: "/"
  },
  icons: {
    icon: [
      { url: "/favicon.ico", sizes: "any" },
      { url: "/brand/icon-192.png", type: "image/png", sizes: "192x192" },
      { url: "/brand/icon-512.png", type: "image/png", sizes: "512x512" }
    ],
    apple: [
      { url: "/brand/apple-touch-icon.png", type: "image/png", sizes: "180x180" }
    ],
    shortcut: [{ url: "/favicon.ico" }]
  },
  keywords: [
    "نطاق",
    "الباحث العام",
    "بحث عام",
    "أداة بحث عربية",
    "روابط بحث آمنة",
    "بحث Google",
    "بحث منصات اجتماعية",
    "خصوصية"
  ],
  authors: [{ name: "نطاق" }],
  creator: "نطاق",
  publisher: "نطاق",
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
    url: siteUrl,
    siteName: siteTitle,
    title: siteTitle,
    description: siteDescription,
    images: [
      {
        url: socialImage,
        width: 1200,
        height: 630,
        alt: "نطاق | الباحث العام"
      }
    ]
  },
  twitter: {
    card: "summary_large_image",
    title: siteTitle,
    description: siteDescription,
    images: [socialImage]
  },
  appleWebApp: {
    capable: true,
    title: siteTitle,
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
        {/* Vercel Analytics is initialized once in the root layout so page views are tracked globally after deployment on Vercel. */}
        <Analytics />
      </body>
    </html>
  );
}
