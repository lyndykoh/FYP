import type { Metadata } from "next";
import { AppRouterCacheProvider } from "@mui/material-nextjs/v16-appRouter";
import CssBaseline from "@mui/material/CssBaseline";
import ThemeProvider from "./theme-provider";
import SidebarProvider from "../providers/sidebar-provider";
import ViewportProvider from "@/providers/viewport-provider";
import "./globals.css";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Lumina Academy",
  description: "Tutor and student learning workspace",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <AppRouterCacheProvider>
          <ThemeProvider>
            <CssBaseline />
            <ViewportProvider>
              <SidebarProvider>
                <div
                  style={{
                    minHeight: "100vh",
                    display: "flex",
                    flexDirection: "column",
                  }}
                >
                  {children}
                  <footer
                    style={{
                      borderTop: "1px solid #EDE6DB",
                      padding: "18px 24px",
                      display: "flex",
                      gap: "18px",
                      justifyContent: "center",
                      flexWrap: "wrap",
                      background: "#FAF7F2",
                    }}
                  >
                    <Link href="/privacy" style={{ color: "#5F574E" }}>
                      Privacy Policy
                    </Link>
                    <Link href="/terms" style={{ color: "#5F574E" }}>
                      Terms of Use
                    </Link>
                  </footer>
                </div>
              </SidebarProvider>
            </ViewportProvider>
          </ThemeProvider>
        </AppRouterCacheProvider>
      </body>
    </html>
  );
}
