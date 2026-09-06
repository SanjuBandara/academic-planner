import { forwardRef, type InputHTMLAttributes } from "react";

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

const FormField = forwardRef<HTMLInputElement, FormFieldProps>(
  ({ label, error, id, ...rest }, ref) => {
    return (
      <div className="mb-5">
        <label htmlFor={id} className="mb-1.5 block text-sm text-slate">
          {label}
        </label>
        <input
          ref={ref}
          id={id}
          className={`w-full border-0 border-b bg-transparent px-0 py-2 text-ink placeholder:text-slate/40 focus:outline-none focus:ring-0 ${
            error ? "border-red-500" : "border-hairline focus:border-gold"
          }`}
          style={{ transition: "border-color 150ms ease" }}
          {...rest}
        />
        {error && <p className="mt-1.5 text-sm text-red-600">{error}</p>}
      </div>
    );
  }
);

FormField.displayName = "FormField";

export default FormField;
