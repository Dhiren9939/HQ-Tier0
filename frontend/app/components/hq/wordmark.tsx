import { cn } from "~/lib/utils";

/**
 * The mark: a signal passing through a checkpoint. Straight lines and right
 * angles only — the same angularity the rest of the system is built on.
 *
 * Strokes are currentColor, so it inherits whatever text colour it sits in
 * and needs no theme branching. Drawn on a 16-unit grid to stay crisp at
 * favicon size.
 */
export function Mark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
      className={cn("size-4", className)}
    >
      {/* the wire, in and out */}
      <path d="M0 8h3M13 8h3" stroke="currentColor" strokeWidth="1.5" />
      {/* the checkpoint */}
      <rect x="3.75" y="3.75" width="8.5" height="8.5" stroke="currentColor" strokeWidth="1.5" />
      <rect x="6.5" y="6.5" width="3" height="3" fill="currentColor" />
    </svg>
  );
}

export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2 text-foreground", className)}>
      <Mark className="size-[18px] text-primary" />
      <span className="font-display text-lg font-extrabold tracking-tight">HQ</span>
    </span>
  );
}
