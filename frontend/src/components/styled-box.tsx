"use client";

import Box from "@mui/material/Box";
import { styled } from "@mui/material/styles";

const StyledBox = styled(Box)(({ theme }) => ({
  // Uses 90% screen height on mobile/tablet, caps at 650px on desktop
  height: "90dvh", // Ensures it doesn't exceed 90% of the viewport height
  [theme.breakpoints.up("md")]: {
    height: "850px",
  },
  overflowY: "auto", // Enables vertical scrolling when content overflows
  display: "flex",
  flexDirection: "column",
  justifyContent: "center", // Centers the sign-in form
}));

export default StyledBox;
