"use client";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import Checkbox from "@mui/material/Checkbox";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Link from "@mui/material/Link";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import StyledCard from "./styled-card";
import { GoogleIcon, FacebookIcon } from "../utils/icons";
import strings from "../locales/en.json";

interface AuthCardProps {
  handleSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  nameRef?: React.RefObject<HTMLInputElement | null>;
  nameError?: boolean | undefined;
  nameErrorMessage?: string;
  emailRef: React.RefObject<HTMLInputElement | null>;
  emailError: boolean | undefined;
  emailErrorMessage: string;
  passwordRef: React.RefObject<HTMLInputElement | null>;
  passwordError: boolean | undefined;
  passwordErrorMessage: string;
  validateInputs: () => boolean;
  handleClickOpen?: () => void;
  fromSignup: boolean;
  submitErrorMessage?: string;
  isSubmitting?: boolean;
}

export default function AuthCard({
  handleSubmit,
  nameRef,
  nameError,
  nameErrorMessage,
  emailRef,
  emailError,
  emailErrorMessage,
  passwordRef,
  passwordError,
  passwordErrorMessage,
  validateInputs,
  handleClickOpen,
  fromSignup,
  submitErrorMessage,
  isSubmitting = false,
}: AuthCardProps) {
  const isSignup = fromSignup;
  const submitLabel = isSignup ? strings.auth.signup : strings.auth.login;
  const pendingSubmitLabel = isSignup ? "Creating account…" : "Signing in…";

  return (
    <StyledCard variant="outlined" data-testid="auth-card">
      <Typography
        component="h1"
        variant="h4"
        sx={{ width: "100%", fontSize: "clamp(2rem, 10vw, 2.15rem)" }}
      >
        {submitLabel}
      </Typography>
      <Box
        component="form"
        onSubmit={handleSubmit}
        noValidate
        sx={{
          display: "flex",
          flexDirection: "column",
          width: "100%",
          gap: 2,
        }}
      >
        {isSignup && (
          <FormControl>
            <FormLabel htmlFor="fullName">{strings.auth.namePrompt}</FormLabel>
            <TextField
              inputRef={nameRef}
              error={nameError}
              helperText={nameErrorMessage}
              id="fullName"
              type="name"
              name="fullName"
              placeholder="John Doe"
              autoComplete="name"
              autoFocus
              required
              fullWidth
              variant="outlined"
              color={nameError ? "error" : "primary"}
            />
          </FormControl>
        )}
        <FormControl>
          <FormLabel htmlFor="email">{strings.auth.emailPrompt}</FormLabel>
          <TextField
            inputRef={emailRef}
            error={emailError}
            helperText={emailErrorMessage}
            id="email"
            type="email"
            name="email"
            placeholder="your@email.com"
            autoComplete="email"
            autoFocus
            required
            fullWidth
            variant="outlined"
            color={emailError ? "error" : "primary"}
          />
        </FormControl>
        <FormControl>
          <FormLabel htmlFor="password">
            {strings.auth.passwordPrompt}
          </FormLabel>
          <TextField
            inputRef={passwordRef}
            error={passwordError}
            helperText={passwordErrorMessage}
            name="password"
            placeholder="••••••"
            type="password"
            id="password"
            autoComplete={isSignup ? "new-password" : "current-password"}
            autoFocus
            required
            fullWidth
            variant="outlined"
            color={passwordError ? "error" : "primary"}
          />
        </FormControl>
        {!fromSignup && (
          <FormControlLabel
            control={<Checkbox value="remember" color="primary" />}
            label={strings.auth.rememberMe}
          />
        )}
        {submitErrorMessage ? (
          <Alert severity="error" sx={{ width: "100%" }}>
            {submitErrorMessage}
          </Alert>
        ) : null}
        <Button
          type="submit"
          fullWidth
          variant="contained"
          onClick={validateInputs}
          disabled={isSubmitting}
          aria-busy={isSubmitting}
        >
          {isSubmitting ? pendingSubmitLabel : submitLabel}
        </Button>
        {!fromSignup && (
          <Link
            component="button"
            type="button"
            onClick={handleClickOpen}
            variant="body2"
            sx={{ alignSelf: "center" }}
          >
            {strings.auth.forgotPassword}
          </Link>
        )}
      </Box>
      <Divider>or</Divider>
      <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
        <Button
          fullWidth
          variant="outlined"
          onClick={() => alert("Sign in with Google")}
          startIcon={<GoogleIcon />}
        >
          {isSignup
            ? strings.auth.continueWithGoogle
            : strings.auth.signInWithGoogle}
        </Button>
        <Button
          fullWidth
          variant="outlined"
          onClick={() => alert("Sign in with Facebook")}
          startIcon={<FacebookIcon />}
        >
          {isSignup
            ? strings.auth.continueWithFacebook
            : strings.auth.signInWithFacebook}
        </Button>
        {!isSignup ? (
          <Typography sx={{ textAlign: "center" }}>
            {strings.auth.noAccount}
            <Link href="/signup" variant="body2" sx={{ alignSelf: "center" }}>
              {strings.auth.signup}
            </Link>
          </Typography>
        ) : (
          <Typography sx={{ textAlign: "center" }}>
            {strings.auth.haveAccount}
            <Link href="/login" variant="body2" sx={{ alignSelf: "center" }}>
              {strings.auth.login}
            </Link>
          </Typography>
        )}
      </Box>
    </StyledCard>
  );
}
