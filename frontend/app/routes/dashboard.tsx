import { useEffect, useState } from "react";
import { useNavigate } from "react-router";

import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { ThemeToggle } from "~/components/theme/theme-toggle";
import { Wordmark } from "~/components/hq/wordmark";
import { fetchMe, logout, type User } from "~/lib/api";

type State = { status: "loading" } | { status: "ready"; user: User } | { status: "redirecting" };

export default function Dashboard() {
  const navigate = useNavigate();
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;

    fetchMe()
      .then((user) => {
        if (!cancelled) setState({ status: "ready", user });
      })
      .catch(() => {
        if (cancelled) return;
        // Anything that fails here - UnauthenticatedError or otherwise - sends the visitor
        // back to sign in; there's nothing useful to show on this page without a session.
        setState({ status: "redirecting" });
        navigate("/", { replace: true });
      });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  async function handleLogout() {
    await logout();
    navigate("/", { replace: true });
  }

  return (
    <div className="flex min-h-svh items-center justify-center bg-background p-4">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <div className="w-full max-w-sm">
        <Wordmark className="mb-6 justify-center" />
        <Card>
          <CardHeader>
            <CardTitle className="font-display text-xl font-extrabold tracking-tight">
              Dashboard
            </CardTitle>
            <CardDescription>You're signed in.</CardDescription>
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
      </div>
    </div>
  );
}
