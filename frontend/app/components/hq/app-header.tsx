import { Link, useLocation } from "react-router";

import { ThemeToggle } from "~/components/theme/theme-toggle";
import { Wordmark } from "~/components/hq/wordmark";
import { cn } from "~/lib/utils";

const NAV_LINKS = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/dashboard/tenants", label: "Tenants" },
  { href: "/dashboard/api-keys", label: "API keys" },
];

/** Signed-in chrome shared by every /dashboard/* page: wordmark, section nav, theme toggle. */
export function AppHeader() {
  const location = useLocation();

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background">
      <div className="mx-auto flex h-14 max-w-5xl items-center gap-6 px-6">
        <Link to="/dashboard" aria-label="HQ home" className="shrink-0">
          <Wordmark />
        </Link>
        <nav className="flex items-center gap-1">
          {NAV_LINKS.map((link) => {
            const active = location.pathname === link.href;
            return (
              <Link
                key={link.href}
                to={link.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "rounded-lg px-2.5 py-1.5 text-sm font-medium transition-colors hover:bg-muted hover:text-foreground",
                  active ? "text-foreground" : "text-muted-foreground"
                )}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>
        <div className="ml-auto flex items-center gap-3">
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
