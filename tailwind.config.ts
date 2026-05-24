import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}"
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          "var(--font-arabic)",
          "Tahoma",
          "Arial",
          "sans-serif"
        ]
      },
      colors: {
        ink: "#111827",
        sand: "#f7f3ec",
        palm: "#0f8f72",
        date: "#8b5e34",
        night: "#08111f"
      },
      boxShadow: {
        glow: "0 20px 80px rgba(15, 143, 114, 0.22)",
        soft: "0 16px 50px rgba(17, 24, 39, 0.10)"
      }
    }
  },
  plugins: []
};

export default config;
