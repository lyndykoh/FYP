"use client";

import { styled } from "@mui/material/styles";
import Stack from "@mui/material/Stack";

const StyledStack = styled(Stack)(() => ({
  height: "calc((1 - var(--template-frame-height, 0)) * 100dvh)",
  minHeight: "100%",
  "&::before": {
    content: '""',
    display: "block",
    position: "absolute",
    zIndex: -1,
    inset: 0,
  },
}));

export default StyledStack;
