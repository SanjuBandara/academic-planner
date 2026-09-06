import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate } from "react-router-dom";
import { registerSchema, type RegisterFormValues } from "../../components/auth/authSchemas";
import FormField from "../../components/auth/FormField";
import ErrorBanner from "../../components/auth/ErrorBanner";
import AuthButton from "../../components/auth/AuthButton";
import AuthLayout from "../../components/auth/AuthLayout";
import { useRegister } from "../../hooks/useAuth";
import { getApiErrorMessage } from "../../api/errorUtils";

export default function Register() {
  const navigate = useNavigate();
  const registerMutation = useRegister();
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = async (values: RegisterFormValues) => {
    setApiError(null);
    try {
      await registerMutation.mutateAsync({
        email: values.email,
        password: values.password,
      });
      navigate("/login", { replace: true, state: { justRegistered: true } });
    } catch (error) {
      setApiError(getApiErrorMessage(error, "Registration failed. Please try again."));
    }
  };

  return (
    <AuthLayout
      title="Create an account"
      subtitle="Start organizing your academic workload and study plans."
    >
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
          autoComplete="new-password"
          error={errors.password?.message}
          {...register("password")}
        />
        <FormField
          id="confirmPassword"
          label="Confirm Password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register("confirmPassword")}
        />

        <AuthButton
          loading={isSubmitting || registerMutation.isPending}
          loadingLabel="Creating account..."
        >
          Register
        </AuthButton>
      </form>

      <p className="mt-6 text-center text-sm text-slate">
        Already have an account?{" "}
        <Link to="/login" className="font-medium text-ink underline decoration-hairline underline-offset-4 hover:decoration-ink">
          Log in
        </Link>
      </p>
    </AuthLayout>
  );
}
