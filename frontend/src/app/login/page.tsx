import Login from "./login";
import StyledBox from "@/components/styled-box";

export default function Page() {
  return (
    <StyledBox sx={{ mt: 4 }} data-testid="login-page">
      <Login />
    </StyledBox>
  );
}
