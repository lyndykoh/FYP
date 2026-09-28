import type { NextConfig } from "next";

function configuredOrigin(value: string | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:"
      ? url.origin
      : null;
  } catch {
    return null;
  }
}

const apiOrigins = [
  process.env.NEXT_PUBLIC_API_URL,
  process.env.NEXT_PUBLIC_LEARNING_API_URL,
  process.env.NEXT_PUBLIC_GRADING_API_URL,
]
  .map(configuredOrigin)
  .filter((value): value is string => value !== null);

const contentSecurityPolicy = [
  "default-src 'self'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "object-src 'none'",
  "script-src 'self' 'unsafe-inline'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  `connect-src 'self' ${apiOrigins.join(" ")}`,
].join("; ");

const nextConfig: NextConfig = {
  agentRules: false,
  output: "standalone",
  poweredByHeader: false,
  async headers() {
    const headers = [
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      {
        key: "Permissions-Policy",
        value: "geolocation=(), microphone=(), payment=(), usb=()",
      },
      { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
      { key: "Content-Security-Policy", value: contentSecurityPolicy },
    ];

    // TLS may terminate at a reverse proxy. Set this only where that proxy
    // guarantees HTTPS; applying HSTS to local HTTP would make development
    // inaccessible after the first response.
    if (process.env.ENFORCE_HTTPS === "true") {
      headers.push({
        key: "Strict-Transport-Security",
        value: "max-age=31536000; includeSubDomains",
      });
    }

    return [{ source: "/(.*)", headers }];
  },
  turbopack: {
    root: process.cwd(),
  },
  // devIndicators: false, // Disables the indicator badge entirely
};

export default nextConfig;
