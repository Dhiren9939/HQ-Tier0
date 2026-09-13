import { Moon, Sun } from "lucide-react";

import { Button } from "~/components/ui/button";

import { useTheme } from "./theme-provider";

/**
 * Deliberately minimal: a single icon button that flips light/dark, not the
 * full light/dark/system button group. No border, no label — just enough to
 * check a theme during development without competing with the page content.
 */
export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const isDark = resolvedTheme === "dark";

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon-xs"
      aria-label={isDark ? "Switch to light theme" : "Switch to dark theme"}
      title={isDark ? "Switch to light theme" : "Switch to dark theme"}
      onClick={() => setTheme(isDark ? "light" : "dark")}
    >
      {isDark ? <Sun /> : <Moon />}
    </Button>
  );
}
