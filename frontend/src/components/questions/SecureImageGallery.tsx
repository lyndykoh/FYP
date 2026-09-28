"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

export interface SecureImageItem {
  id: number;
  filename: string;
  width: number;
  height: number;
}

/** Browser image tags cannot attach a bearer token, so every protected image is fetched as a Blob first. */
export default function SecureImageGallery({
  images,
  loadImage,
  altPrefix = "Question diagram",
}: {
  images: readonly SecureImageItem[];
  loadImage: (imageId: number) => Promise<string>;
  altPrefix?: string;
}) {
  const [urls, setUrls] = React.useState<Record<number, string>>({});
  React.useEffect(() => {
    let active = true;
    const allocated: string[] = [];
    void Promise.all(
      images.map(
        async (image) => [image.id, await loadImage(image.id)] as const,
      ),
    )
      .then((entries) => {
        if (!active) {
          entries.forEach(([, url]) => URL.revokeObjectURL(url));
          return;
        }
        entries.forEach(([, url]) => allocated.push(url));
        setUrls(Object.fromEntries(entries));
      })
      .catch(() => {
        /* Text remains usable when a protected image is unavailable. */
      });
    return () => {
      active = false;
      allocated.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [images, loadImage]);
  if (images.length === 0) return null;
  return (
    <Box sx={{ display: "grid", gap: 1.25, mt: 1.5 }}>
      {images.map((image, index) =>
        urls[image.id] ? (
          <Box
            key={image.id}
            component="figure"
            sx={{ m: 0, maxWidth: "100%" }}
          >
            <Box
              component="img"
              src={urls[image.id]}
              alt={`${altPrefix} ${index + 1}: ${image.filename}`}
              sx={{
                display: "block",
                maxWidth: "100%",
                height: "auto",
                maxHeight: 520,
                objectFit: "contain",
                border: "1px solid #EBE4D9",
                borderRadius: "8px",
                bgcolor: "#fff",
              }}
            />
          </Box>
        ) : (
          <Typography
            key={image.id}
            role="status"
            sx={{ color: "#8B837A", fontSize: 12 }}
          >
            Loading diagram…
          </Typography>
        ),
      )}
    </Box>
  );
}
