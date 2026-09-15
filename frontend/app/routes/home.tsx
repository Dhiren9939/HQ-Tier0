import { useEffect, useState } from "react";
import { Link } from "react-router";

import { Button } from "~/components/ui/button";
import { Card, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { ThemeToggle } from "~/components/theme/theme-toggle";
import { Wordmark } from "~/components/hq/wordmark";
import { fetchProfile } from "~/lib/api";

import type { Route } from "./+types/home";

const TITLE = "HQ — Webhook delivery infrastructure";
const DESCRIPTION =
  "HQ signs, queues, and retries every webhook you send, and keeps a searchable record of what happened to it.";

export function meta({}: Route.MetaArgs) {
  return [
    { title: TITLE },
    { name: "description", content: DESCRIPTION },
  ];
}

const FEATURES = [
  {
    status: "delivered" as const,
    title: "Signed & verified",
    description: "Every payload is signed with HMAC-SHA256 so receivers can trust it's really from you.",
  },
  {
    status: "retrying" as const,
    title: "Retried automatically",
    description: "Five attempts over six hours with exponential backoff — no dropped events on a flaky endpoint.",
  },
  {
    status: "failed" as const,
    title: "Searchable history",
    description: "30 days of delivery history and replay, so a failed webhook is never a mystery.",
  },
];

const STATUS_DOT_CLASS: Record<(typeof FEATURES)[number]["status"], string> = {
  delivered: "bg-status-delivered",
  retrying: "bg-status-retrying",
  failed: "bg-status-failed",
};

/** Swaps the header/hero CTA to "Go to dashboard" once a session is confirmed. Never redirects on its own — this is a public page. */
function useAuthState() {
  const [authed, setAuthed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchProfile()
      .then(() => {
        if (!cancelled) setAuthed(true);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  return authed;
}

export default function Home() {
  const authed = useAuthState();
  const ctaHref = authed ? "/dashboard" : "/login";
  const ctaLabel = authed ? "Go to dashboard" : "Sign in";

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="sticky top-0 z-50 border-b border-border bg-background">
        <div className="mx-auto flex h-14 max-w-5xl items-center px-6">
          <Link to="/" aria-label="HQ home" className="shrink-0">
            <Wordmark />
          </Link>
          <div className="ml-auto flex items-center gap-3">
            <ThemeToggle />
            <Button asChild size="sm">
              <Link to={ctaHref}>{ctaLabel}</Link>
            </Button>
          </div>
        </div>
      </header>

      <main className="flex-1">
        <section className="mx-auto max-w-5xl px-6 py-20 sm:py-28">
          <p className="label-micro mb-4">HQ</p>
          <h1 className="max-w-[20ch] text-4xl font-extrabold tracking-tight text-balance sm:text-5xl">
            Webhook delivery infrastructure
          </h1>
          <p className="mt-4 max-w-[58ch] text-muted-foreground">{DESCRIPTION}</p>
          <div className="mt-8">
            <Button asChild size="xl">
              <Link to={ctaHref}>{ctaLabel}</Link>
            </Button>
          </div>
        </section>

        <section className="mx-auto max-w-5xl px-6 pb-20 sm:pb-28">
          <div className="grid gap-4 sm:grid-cols-3">
            {FEATURES.map((feature) => (
              <Card key={feature.title}>
                <CardHeader>
                  <div className="mb-1 flex items-center gap-2">
                    <span className={`size-1.5 shrink-0 rounded-full ${STATUS_DOT_CLASS[feature.status]}`} />
                    <CardTitle className="font-display text-base font-bold tracking-tight">
                      {feature.title}
                    </CardTitle>
                  </div>
                  <CardDescription>{feature.description}</CardDescription>
                </CardHeader>
              </Card>
            ))}
          </div>
        </section>
      </main>

      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-6">
          <Wordmark className="opacity-70" />
          <p className="label-micro">© {new Date().getFullYear()} HQ</p>
        </div>
      </footer>
    </div>
  );
}
