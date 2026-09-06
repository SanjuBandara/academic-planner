import type { ReactNode } from "react";

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
}

/**
 * Shared shell for the login and register pages. Left panel carries the
 * app identity; right panel holds the form. Collapses to a compact top
 * banner on narrow screens.
 */
export default function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      {/* Identity panel */}
      <div className="relative flex shrink-0 flex-col justify-between overflow-hidden bg-ink px-8 py-10 text-paper md:w-[38%] md:px-12 md:py-14">
        <div>
          <p className="font-serif text-2xl tracking-tight">Academic Planner</p>
        </div>

        {/* Weekly-timetable motif: quiet horizontal rules, evokes a class schedule grid */}
        <div className="my-10 hidden flex-1 flex-col justify-center gap-3 md:flex" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              className="h-px bg-ink-light"
              style={{ width: `${100 - i * 12}%` }}
            />
          ))}
        </div>

        <p className="max-w-xs text-sm leading-relaxed text-paper/70">
          One place for your semesters, modules, assessments, and the work
          still ahead of you.
        </p>
      </div>

      {/* Form panel */}
      <div className="flex flex-1 items-center justify-center px-6 py-12 md:px-10">
        <div className="w-full max-w-sm">
          <h1 className="font-serif text-3xl text-ink">{title}</h1>
          <p className="mt-2 text-sm text-slate">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </div>
  );
}
