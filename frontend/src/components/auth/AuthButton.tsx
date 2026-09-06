import type { ButtonHTMLAttributes } from "react";

interface AuthButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean;
  loadingLabel?: string;
}

export default function AuthButton({
  loading,
  loadingLabel = "Please wait...",
  children,
  disabled,
  ...rest
}: AuthButtonProps) {
  return (
    <button
      type="submit"
      disabled={disabled || loading}
      className="mt-2 w-full bg-ink py-2.5 text-sm font-medium text-paper transition-colors hover:bg-ink-light disabled:cursor-not-allowed disabled:opacity-50"
      {...rest}
    >
      {loading ? loadingLabel : children}
    </button>
  );
}
