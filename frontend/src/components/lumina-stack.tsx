import * as React from "react";
import Box from "@mui/material/Box";
import type { SxProps, Theme } from "@mui/material/styles";

type Responsive<T> = T | Record<string, T>;

type LuminaStackProps = {
  children: React.ReactNode;
  component?: React.ElementType;
  href?: string;
  direction?: Responsive<React.CSSProperties["flexDirection"]>;
  gap?: Responsive<React.CSSProperties["gap"]>;
  justifyContent?: Responsive<React.CSSProperties["justifyContent"]>;
  alignItems?: Responsive<React.CSSProperties["alignItems"]>;
  flexWrap?: Responsive<React.CSSProperties["flexWrap"]>;
  sx?: SxProps<Theme>;
};

/**
 * A small MUI flex primitive that keeps layout props inside `sx`.
 *
 * MUI v9 no longer consumes several System layout props directly on Stack in
 * this project, so passing them through can leave invalid React attributes on
 * the rendered DOM node.
 */
export default function LuminaStack({
  children,
  component,
  href,
  direction,
  gap,
  justifyContent,
  alignItems,
  flexWrap,
  sx,
}: LuminaStackProps) {
  const StackBox = Box as React.ElementType;

  return (
    <StackBox
      component={component}
      href={href}
      sx={[
        {
          display: "flex",
          flexDirection: direction,
          gap,
          justifyContent,
          alignItems,
          flexWrap,
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      {children}
    </StackBox>
  );
}
