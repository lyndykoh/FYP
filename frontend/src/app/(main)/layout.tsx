import AuthGuard from "./auth-guard";
import Wrapper from "./wrapper";

export default function MainLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <AuthGuard>
      <Wrapper>{children}</Wrapper>
    </AuthGuard>
  );
}
