import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import apiClient from "../api/client.js";

function validate(values) {
  const errors = {};
  if (!values.name.trim()) errors.name = "Enter your name.";
  if (!values.email.trim()) errors.email = "Enter your email.";
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) errors.email = "Enter a valid email address.";
  if (values.initialDeposit.trim()) {
    if (!/^\d+(\.\d{1,2})?$/.test(values.initialDeposit.trim()) || Number(values.initialDeposit) <= 0) {
      errors.initialDeposit = "Enter an amount greater than 0 with up to 2 decimal places.";
    }
  }
  return errors;
}

export default function CreateAccountModal({ onClose, onCreated, returnFocusRef }) {
  const [values, setValues] = useState({ name: "", email: "", initialDeposit: "" });
  const [errors, setErrors] = useState({});
  const nameRef = useRef(null);
  const emailRef = useRef(null);
  const depositRef = useRef(null);
  const dialogRef = useRef(null);
  const queryClient = useQueryClient();
  const create = useMutation({
    mutationFn: async (payload) => (await apiClient.post("/accounts", payload)).data,
    onSuccess: (account) => {
      queryClient.setQueryData(["account", account.id], account);
      onCreated(account);
    },
    onError: (error) => {
      if (error.code === "EMAIL_ALREADY_EXISTS" || error.status === 409) emailRef.current?.focus();
    },
  });
  const duplicateEmail = create.error?.code === "EMAIL_ALREADY_EXISTS" || create.error?.status === 409;
  const serverMessage = create.error?.error || create.error?.message || "";
  const emailError = errors.email || (duplicateEmail ? serverMessage : "");
  const formError = create.error && !duplicateEmail ? serverMessage : "";

  useEffect(() => {
    nameRef.current?.focus();
    return () => returnFocusRef?.current?.focus();
  }, [returnFocusRef]);

  function close() {
    if (!create.isPending) onClose();
  }

  function handleKeyDown(event) {
    if (event.key === "Escape") {
      event.preventDefault();
      close();
    }
    if (event.key !== "Tab") return;
    const controls = [...dialogRef.current.querySelectorAll("input:not([disabled]), button:not([disabled])")];
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (!dialogRef.current.contains(document.activeElement)) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    } else if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function submit(event) {
    event.preventDefault();
    setErrors({});
    create.reset();
    const validationErrors = validate(values);
    if (Object.keys(validationErrors).length) {
      setErrors(validationErrors);
      const firstInvalid = ["name", "email", "initialDeposit"].find((field) => validationErrors[field]);
      ({ name: nameRef, email: emailRef, initialDeposit: depositRef })[firstInvalid].current?.focus();
      return;
    }
    const payload = { name: values.name.trim(), email: values.email.trim() };
    if (values.initialDeposit.trim()) payload.initial_deposit = Number(values.initialDeposit);
    create.mutate(payload);
  }

  function update(field, value) {
    setValues((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: "" }));
    create.reset();
  }

  const fieldClass = "w-full rounded-sm border border-border bg-background px-3 py-3 text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";
  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center overflow-y-auto bg-background/80 p-4" onMouseDown={(event) => {
      if (event.target === event.currentTarget) close();
    }}>
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-account-title"
        onKeyDown={handleKeyDown}
        className="my-auto max-h-[calc(100vh-2rem)] w-full max-w-[480px] overflow-y-auto rounded-xl border border-border bg-surface p-5 sm:p-6"
      >
        <div className="flex items-center justify-between gap-4">
          <h2 id="create-account-title" tabIndex="-1" className="text-xl font-bold">Create account</h2>
          <button type="button" aria-label="Close create account dialog" onClick={close} disabled={create.isPending}
            className="rounded-full px-3 py-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent">×</button>
        </div>
        <form className="mt-5 space-y-4" onSubmit={submit} noValidate>
          <Field label="Name" name="name" value={values.name} error={errors.name} required inputRef={nameRef} onChange={update} className={fieldClass} />
          <Field label="Email" name="email" type="email" value={values.email} error={emailError} serverError={Boolean(duplicateEmail && !errors.email)} required inputRef={emailRef} onChange={update} className={fieldClass} />
          <Field label="Initial deposit (optional)" name="initialDeposit" value={values.initialDeposit} error={errors.initialDeposit} inputMode="decimal" inputRef={depositRef} onChange={update} className={fieldClass} />
          {formError && <p className="text-sm text-danger" role="alert">{formError}</p>}
          {create.isPending && <p className="text-sm text-muted" role="status">Creating account…</p>}
          <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end">
            <button type="button" onClick={close} disabled={create.isPending}
              className="rounded-md border border-border px-5 py-3 hover:border-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent">Cancel</button>
            <button type="submit" disabled={create.isPending}
              className="rounded-md bg-accent px-5 py-3 font-medium text-on-accent hover:underline disabled:cursor-not-allowed disabled:bg-background disabled:text-muted">
              {create.isPending ? "Creating account…" : "Create account"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function Field({ label, name, type = "text", value, error, serverError = false, required = false, inputMode, inputRef, onChange, className }) {
  const errorId = `${name}-error`;
  return (
    <div>
      <label htmlFor={`create-${name}`} className="mb-2 block text-sm font-medium">{label}</label>
      <input id={`create-${name}`} ref={inputRef} name={name} type={type} value={value} required={required} inputMode={inputMode}
        onChange={(event) => onChange(name, event.target.value)}
        aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined} className={className} />
      {error && <p id={errorId} className="mt-2 text-sm text-danger" role={serverError ? "alert" : "status"} aria-live={serverError ? "assertive" : "polite"}>{error}</p>}
    </div>
  );
}
