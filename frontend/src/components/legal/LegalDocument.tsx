"use client";

import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Link from "@mui/material/Link";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import NextLink from "next/link";

export type LegalSection = {
  heading: string;
  paragraphs: string[];
};

type LegalDocumentProps = {
  title: string;
  summary: string;
  sections: LegalSection[];
};

/** Shared, deliberately plain presentation for the public legal documents. */
export default function LegalDocument({
  title,
  summary,
  sections,
}: LegalDocumentProps) {
  return (
    <Box
      component="main"
      sx={{ minHeight: "100vh", bgcolor: "#FAF7F2", py: { xs: 5, sm: 8 } }}
    >
      <Container maxWidth="md">
        <Stack spacing={3}>
          <Box
            component="nav"
            aria-label="Legal navigation"
            sx={{ display: "flex", gap: 2 }}
          >
            <Link component={NextLink} href="/privacy" color="inherit">
              Privacy Policy
            </Link>
            <Link component={NextLink} href="/terms" color="inherit">
              Terms of Use
            </Link>
            <Link component={NextLink} href="/" color="inherit">
              Back to Lumina Academy
            </Link>
          </Box>
          <Box>
            <Typography
              component="h1"
              variant="h3"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                color: "#2A2622",
              }}
            >
              {title}
            </Typography>
            <Typography sx={{ mt: 1.5, color: "#5F574E", maxWidth: 680 }}>
              {summary}
            </Typography>
          </Box>
          <Paper
            component="article"
            elevation={0}
            sx={{
              p: { xs: 2.5, sm: 4 },
              border: "1px solid #EDE6DB",
              bgcolor: "#FFFDFA",
            }}
          >
            <Stack spacing={3}>
              {sections.map((section) => (
                <Box component="section" key={section.heading}>
                  <Typography
                    component="h2"
                    variant="h6"
                    sx={{ color: "#2A2622", mb: 1 }}
                  >
                    {section.heading}
                  </Typography>
                  {section.paragraphs.map((paragraph) => (
                    <Typography
                      key={paragraph}
                      component="p"
                      sx={{ color: "#514A43", lineHeight: 1.7, mb: 1.25 }}
                    >
                      {paragraph}
                    </Typography>
                  ))}
                </Box>
              ))}
            </Stack>
          </Paper>
          <Typography component="p" variant="body2" sx={{ color: "#6C6258" }}>
            These documents describe the current application build. The
            deployment operator is responsible for keeping them accurate as
            configuration, providers, and applicable law change.
          </Typography>
        </Stack>
      </Container>
    </Box>
  );
}
