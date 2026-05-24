"use client";

import { useEffect } from "react";

export function PwaRegister() {
  useEffect(() => {
    const isSupported = "serviceWorker" in navigator;
    const isSafeOrigin =
      window.location.protocol === "https:" ||
      window.location.hostname === "localhost" ||
      window.location.hostname === "127.0.0.1";

    if (!isSupported || !isSafeOrigin) {
      return;
    }

    const register = () => {
      navigator.serviceWorker.register("/sw.js").catch(() => {
        // PWA registration must never block the search experience.
      });
    };

    window.addEventListener("load", register);
    return () => window.removeEventListener("load", register);
  }, []);

  return null;
}
