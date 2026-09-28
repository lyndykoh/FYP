"use client";
import * as React from "react";
import AuthCard from "@/components/auth-card";
import ColorModeSelect from "../../theme/color-mode-select";
import SignInContainer from "../../components/styled-stack";
import ForgotPassword from "../../components/forgot-password";
import strings from "../../locales/en.json";
import { apiRequest, getErrorMessage } from "@/lib/api";
import { useRouter } from "next/navigation";
import {
  getRoleHome,
  saveAuthSession,
  type AuthResponsePayload,
} from "@/lib/auth";
import { isValidEmail, isValidLoginPassword } from "@/lib/validation";

export default function Login() {
  const router = useRouter();
  const emailRef = React.useRef<HTMLInputElement>(null);
  const [emailError, setEmailError] = React.useState(false);
  const [emailErrorMessage, setEmailErrorMessage] = React.useState("");
  const passwordRef = React.useRef<HTMLInputElement>(null);
  const [passwordError, setPasswordError] = React.useState(false);
  const [passwordErrorMessage, setPasswordErrorMessage] = React.useState("");
  const [submitErrorMessage, setSubmitErrorMessage] = React.useState("");
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const submitInFlightRef = React.useRef(false);
  const [open, setOpen] = React.useState(false);

  const handleClickOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitInFlightRef.current || !validateInputs()) {
      return;
    }
    submitInFlightRef.current = true;
    setIsSubmitting(true);
    setSubmitErrorMessage("");
    const data = new FormData(event.currentTarget);
    const email = data.get("email");
    const password = data.get("password");
    try {
      const response = await apiRequest("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      if (!response.ok) {
        setSubmitErrorMessage(await getErrorMessage(response));
        return;
      }
      try {
        const jsonResponse = (await response.json()) as AuthResponsePayload;
        const session = saveAuthSession(jsonResponse);
        router.replace(getRoleHome(session.role));
      } catch {
        setSubmitErrorMessage(
          "Unable to establish a secure session. Please try again.",
        );
      }
    } catch {
      setSubmitErrorMessage(
        "Unable to reach the authentication service. Please try again.",
      );
    } finally {
      submitInFlightRef.current = false;
      setIsSubmitting(false);
    }
  };

  const validateInputs = () => {
    const email = emailRef.current;
    const password = passwordRef.current;

    let isValid = true;

    if (!email || !isValidEmail(email.value)) {
      setEmailError(true);
      setEmailErrorMessage(strings.auth.validation.emailInvalid);
      isValid = false;
    } else {
      setEmailError(false);
      setEmailErrorMessage("");
    }

    if (!password || !isValidLoginPassword(password.value)) {
      setPasswordError(true);
      setPasswordErrorMessage(strings.auth.validation.passwordMinLength);
      isValid = false;
    } else {
      setPasswordError(false);
      setPasswordErrorMessage("");
    }

    return isValid;
  };

  return (
    <SignInContainer
      direction="column"
      sx={{ justifyContent: "space-between" }}
      data-testid="sign-in-container"
    >
      <ColorModeSelect sx={{ position: "fixed", top: "1rem", right: "1rem" }} />
      <ForgotPassword open={open} handleClose={handleClose} />
      <AuthCard
        handleSubmit={handleSubmit}
        emailRef={emailRef}
        emailError={emailError}
        emailErrorMessage={emailErrorMessage}
        passwordRef={passwordRef}
        passwordError={passwordError}
        passwordErrorMessage={passwordErrorMessage}
        validateInputs={validateInputs}
        handleClickOpen={handleClickOpen}
        fromSignup={false}
        submitErrorMessage={submitErrorMessage}
        isSubmitting={isSubmitting}
      />
    </SignInContainer>
  );
}
