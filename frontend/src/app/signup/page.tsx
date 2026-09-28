import SignUp from "./signup";
import StyledBox from "@/components/styled-box";

export default function Page() {
  return (
    <StyledBox sx={{ mt: 4 }} data-testid="signup-page">
      <SignUp />
    </StyledBox>
  );
}
