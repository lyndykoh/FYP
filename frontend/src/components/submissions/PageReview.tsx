"use client";

import type * as React from "react";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Typography from "@mui/material/Typography";

import Stack from "@/components/lumina-stack";
import type { UploadPage } from "@/services/submissions";

type PageReviewProps = {
  pages: UploadPage[];
  onMove: (id: string, direction: -1 | 1) => void;
  onRotate: (id: string) => void;
  onRemove: (id: string) => void;
  onReplace: (id: string, file: File) => void;
};

type PagePreviewProps = {
  page: UploadPage;
  pageNumber: number;
};

const previewContainerSx = {
  width: 70,
  height: 92,
  border: "1px solid #E4DCD0",
  borderRadius: 1,
  overflow: "hidden",
  flexShrink: 0,
  bgcolor: "#F4EFE6",
  display: "grid",
  placeItems: "center",
} as const;

function PagePreview({ page, pageNumber }: PagePreviewProps) {
  if (!page.previewUrl) {
    return (
      <Box sx={previewContainerSx}>
        <Typography sx={{ fontSize: 11, textAlign: "center", px: 0.5 }}>
          PDF
          <br />
          document
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={previewContainerSx}>
      <Box
        component="img"
        src={page.previewUrl}
        alt={`Preview of page ${pageNumber}`}
        sx={{
          maxWidth: "100%",
          maxHeight: "100%",
          transform: `rotate(${page.rotation}deg)`,
        }}
      />
    </Box>
  );
}

type PageControlsProps = {
  page: UploadPage;
  pageIndex: number;
  pageCount: number;
  onMove: PageReviewProps["onMove"];
  onRotate: PageReviewProps["onRotate"];
  onRemove: PageReviewProps["onRemove"];
  onReplace: PageReviewProps["onReplace"];
};

function PageControls({
  page,
  pageIndex,
  pageCount,
  onMove,
  onRotate,
  onRemove,
  onReplace,
}: PageControlsProps) {
  const replacePage = (event: React.ChangeEvent<HTMLInputElement>) => {
    const replacement = event.currentTarget.files?.[0];

    if (replacement) {
      onReplace(page.id, replacement);
    }

    event.currentTarget.value = "";
  };

  return (
    <Stack direction="row" flexWrap="wrap" gap={0.5} sx={{ mt: 0.75 }}>
      <Button
        size="small"
        onClick={() => onMove(page.id, -1)}
        disabled={pageIndex === 0}
      >
        Move up
      </Button>
      <Button
        size="small"
        onClick={() => onMove(page.id, 1)}
        disabled={pageIndex === pageCount - 1}
      >
        Move down
      </Button>
      <Button size="small" onClick={() => onRotate(page.id)}>
        Rotate
      </Button>
      <Button size="small" component="label">
        Replace
        <input
          hidden
          type="file"
          accept="image/jpeg,image/png,application/pdf"
          onChange={replacePage}
        />
      </Button>
      <Button size="small" color="error" onClick={() => onRemove(page.id)}>
        Remove
      </Button>
    </Stack>
  );
}

export default function PageReview({
  pages,
  onMove,
  onRotate,
  onRemove,
  onReplace,
}: PageReviewProps) {
  return (
    <Stack gap={1.25} aria-label="Page review">
      {pages.map((page, index) => {
        const pageNumber = index + 1;

        return (
          <Card
            key={page.id}
            variant="outlined"
            sx={{
              p: 1.5,
              borderColor: "#EBE4D9",
              bgcolor: "#FFFDFA",
              display: "flex",
              gap: 1.5,
            }}
          >
            <PagePreview page={page} pageNumber={pageNumber} />
            <Box sx={{ minWidth: 0, flex: 1 }}>
              <Typography sx={{ fontWeight: 600, fontSize: 13.5 }}>
                Page {pageNumber}
              </Typography>
              <Typography noWrap sx={{ fontSize: 11.5, color: "#6F675E" }}>
                {page.file.name}
              </Typography>
              {page.warning && (
                <Stack direction="row" gap={0.5} sx={{ mt: 0.5 }}>
                  <WarningAmberOutlinedIcon
                    sx={{ fontSize: 14, color: "#7A6238" }}
                  />
                  <Typography sx={{ fontSize: 11.5, color: "#7A6238" }}>
                    {page.warning}
                  </Typography>
                </Stack>
              )}
              <PageControls
                page={page}
                pageIndex={index}
                pageCount={pages.length}
                onMove={onMove}
                onRotate={onRotate}
                onRemove={onRemove}
                onReplace={onReplace}
              />
            </Box>
          </Card>
        );
      })}
    </Stack>
  );
}
