import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router";

import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "~/components/ui/card";
import { ThemeToggle } from "~/components/theme/theme-toggle";
import { Wordmark } from "~/components/hq/wordmark";
import { fetchMe, GOOGLE_LOGIN_URL } from "~/lib/api";

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M23.52 12.27c0-.85-.08-1.67-.22-2.45H12v4.64h6.47a5.54 5.54 0 0 1-2.4 3.63v3h3.87c2.27-2.09 3.58-5.17 3.58-8.82Z"
      />
      <path
        fill="#34A853"
        d="M12 24c3.24 0 5.96-1.07 7.94-2.9l-3.87-3.02c-1.08.72-2.45 1.15-4.07 1.15-3.13 0-5.78-2.11-6.73-4.96H1.28v3.11A12 12 0 0 0 12 24Z"
      />
      <path
        fill="#FBBC05"
        d="M5.27 14.27a7.2 7.2 0 0 1 0-4.54v-3.1H1.28a12 12 0 0 0 0 10.75l3.99-3.11Z"
      />
      <path
        fill="#EA4335"
        d="M12 4.75c1.76 0 3.35.61 4.6 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.28 6.63l3.99 3.1C6.22 6.86 8.87 4.75 12 4.75Z"
      />
    </svg>
  );
}

export default function Login() {
  const location = useLocation();
  const navigate = useNavigate();
  const oauthError = new URLSearchParams(location.search).get("error");

  useEffect(() => {
    // Already have a valid session (e.g. a bookmark to "/") - skip straight past the form.
    // Failure just means "not logged in", which is fine: stay on this page.
    fetchMe()
      .then(() => navigate("/dashboard", { replace: true }))
      .catch(() => {});
  }, [navigate]);

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
              Sign in
            </CardTitle>
            <CardDescription>Continue with your Google account to access HQ.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col gap-4">
              {oauthError && (
                <Alert variant="destructive">
                  <AlertTitle>Couldn&apos;t sign in</AlertTitle>
                  <AlertDescription>
                    Google sign-in didn&apos;t complete. Please try again.
                  </AlertDescription>
                </Alert>
              )}

              <Button asChild variant="outline" className="w-full">
                <a href={GOOGLE_LOGIN_URL}>
                  <GoogleIcon />
                  Continue with Google
                </a>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
