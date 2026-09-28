"use client";
import * as React from "react";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import OutlinedInput from "@mui/material/OutlinedInput";
import strings from "../locales/en.json";

interface ForgotPasswordProps {
  open: boolean;
  handleClose: () => void;
}

export default function ForgotPassword({
  open,
  handleClose,
}: ForgotPasswordProps) {
  const closeAfterSubmitting = (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    handleClose();
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      slotProps={{
        paper: {
          component: "form",
          onSubmit: closeAfterSubmitting,
          sx: { backgroundImage: "none" },
        },
      }}
    >
      <DialogTitle>{strings.auth.reset.title}</DialogTitle>
      <DialogContent
        sx={{ display: "flex", flexDirection: "column", gap: 2, width: "100%" }}
      >
        <DialogContentText>{strings.auth.reset.emailPrompt}</DialogContentText>
        <OutlinedInput
          autoFocus
          required
          margin="dense"
          id="email"
          name="email"
          label={strings.auth.emailPrompt}
          placeholder={strings.auth.emailPrompt}
          type="email"
          fullWidth
        />
      </DialogContent>
      <DialogActions sx={{ pb: 3, px: 3 }}>
        <Button onClick={handleClose}>{strings.auth.reset.cancelButton}</Button>
        <Button variant="contained" type="submit">
          {strings.auth.reset.sendButton}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
