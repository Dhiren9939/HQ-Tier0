import { useEffect, useState } from "react";
import { useNavigate } from "react-router";

import { AppHeader } from "~/components/hq/app-header";
import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { fetchProfile, logout, type User } from "~/lib/api";

type State = { status: "loading" } | { status: "ready"; user: User } | { status: "redirecting" };

export default function Dashboard() {
  const navigate = useNavigate();
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    fetchProfile(controller.signal)
      .then((user) => {
        if (!cancelled) setState({ status: "ready", user });
      })
      .catch(() => {
        if (cancelled) return;
        // Anything that fails here - UnauthenticatedError or otherwise - sends the visitor
        // back to sign in; there's nothing useful to show on this page without a session.
        setState({ status: "redirecting" });
        navigate("/login", { replace: true });
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [navigate]);

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <AppHeader />
      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">
        <h1 className="font-display text-2xl font-extrabold tracking-tight">Dashboard</h1>
        <p className="mt-1 text-sm text-muted-foreground">You're signed in.</p>

        <Card className="mt-6 max-w-sm">
          <CardHeader>
            <CardTitle className="font-display text-base font-bold tracking-tight">Account</CardTitle>
            <CardDescription>Your profile and session.</CardDescription>
          </CardHeader>
          <CardContent>
            {state.status !== "ready" ? (
              <p className="text-sm text-muted-foreground">Loading…</p>
            ) : (
              <div className="flex flex-col gap-4">
                <div className="text-sm">
                  <p className="font-medium">
                    {[state.user.firstName, state.user.lastName].filter(Boolean).join(" ") || "—"}
                  </p>
                  <p className="text-muted-foreground">{state.user.email}</p>
                </div>
                <Button variant="outline" className="w-full" onClick={handleLogout}>
                  Sign out
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
