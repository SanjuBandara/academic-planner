import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { loginSchema, type LoginFormValues } from "../../components/auth/authSchemas";
import FormField from "../../components/auth/FormField";
import ErrorBanner from "../../components/auth/ErrorBanner";
import AuthButton from "../../components/auth/AuthButton";
import AuthLayout from "../../components/auth/AuthLayout";
import { useLogin } from "../../hooks/useAuth";
import { getApiErrorMessage } from "../../api/errorUtils";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const loginMutation = useLogin();
  const [apiError, setApiError] = useState<string | null>(null);

  const justRegistered = Boolean((location.state as { justRegistered?: boolean } | null)?.justRegistered);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (values: LoginFormValues) => {
    setApiError(null);
    try {
      await loginMutation.mutateAsync(values);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setApiError(getApiErrorMessage(error, "Invalid email or password."));
    }
  };

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Log in to access your modules, schedules, and study tasks."
    >
      {justRegistered && !apiError && (
        <div className="mb-4 rounded-md border border-emerald-300/40 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          Account created successfully. Please log in.
        </div>
      )}

      <ErrorBanner message={apiError} />

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register("email")}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register("password")}
        />

        <AuthButton
          loading={isSubmitting || loginMutation.isPending}
          loadingLabel="Logging in..."
        >
          Log in
        </AuthButton>
      </form>

      <p className="mt-6 text-center text-sm text-slate">
        Don't have an account?{" "}
        <Link to="/register" className="font-medium text-ink underline decoration-hairline underline-offset-4 hover:decoration-ink">
          Register
        </Link>
      </p>
    </AuthLayout>
  );
}
