import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { ChevronLeft, ChevronRight, Check, Copy, KeyRound, Loader2, Pencil, Plus, Trash2 } from "lucide-react";

import { AppHeader } from "~/components/hq/app-header";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { Input } from "~/components/ui/input";
import { Label } from "~/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "~/components/ui/select";
import {
  ApiRequestError,
  createApiKey,
  deleteApiKey,
  fetchProfile,
  listApiKeys,
  patchApiKey,
  type ApiKey,
  type ApiKeyCreated,
  type ApiKeyExpiry,
  type ApiKeyPage,
} from "~/lib/api";

type AuthState = "loading" | "ready" | "redirecting";

const EXPIRY_OPTIONS: { value: ApiKeyExpiry; label: string }[] = [
  { value: "ONE_DAY", label: "1 day" },
  { value: "ONE_MONTH", label: "1 month" },
  { value: "NEVER", label: "Never" },
];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function expiryLabel(key: ApiKey): string {
  return key.expiresAt ? `Expires ${formatDate(key.expiresAt)}` : "Never expires";
}

function keyStatus(key: ApiKey): { label: string; variant: "success" | "destructive" } {
  if (key.expiresAt && new Date(key.expiresAt).getTime() <= Date.now()) {
    return { label: "Expired", variant: "destructive" };
  }
  return { label: "Active", variant: "success" };
}

/** Best-effort message for a failed mutation - falls back for network errors, which carry no ApiError. */
function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiRequestError) return err.apiError?.message ?? fallback;
  return fallback;
}

export default function ApiKeysPage() {
  const navigate = useNavigate();
  const [authState, setAuthState] = useState<AuthState>("loading");
  const [pageData, setPageData] = useState<ApiKeyPage | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<ApiKey | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ApiKey | null>(null);

  useEffect(() => {
    let cancelled = false;
    // Tracks whether the profile fetch itself succeeded - not React state, since the closure
    // below would otherwise see the state value from the render that started this effect, not
    // whatever setAuthState("ready") set it to a moment ago.
    let profileConfirmed = false;

    fetchProfile()
      .then(() => {
        if (cancelled) return;
        profileConfirmed = true;
        setAuthState("ready");
        return listApiKeys(0);
      })
      .then((fetched) => {
        if (!cancelled && fetched) setPageData(fetched);
      })
      .catch((err) => {
        if (cancelled) return;
        if (!profileConfirmed) {
          // Failed before we ever confirmed a session - treat like the dashboard does.
          setAuthState("redirecting");
          navigate("/login", { replace: true });
          return;
        }
        setListError(errorMessage(err, "Couldn't load API keys."));
      });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  async function reloadKeys(page = pageData?.pageNo ?? 0) {
    try {
      const fetched = await listApiKeys(page);
      // Deleting the last key on a page beyond the first empties it - fall back a page instead
      // of showing a dead end.
      if (fetched.apiKeyList.length === 0 && fetched.pageNo > 0) {
        return reloadKeys(fetched.pageNo - 1);
      }
      setPageData(fetched);
      setListError(null);
    } catch (err) {
      setListError(errorMessage(err, "Couldn't load API keys."));
    }
  }

  if (authState !== "ready") {
    return (
      <div className="flex min-h-svh flex-col bg-background">
        <AppHeader />
        <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">
          <p className="text-sm text-muted-foreground">Loading…</p>
        </main>
      </div>
    );
  }

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <AppHeader />
      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="font-display text-2xl font-extrabold tracking-tight">API keys</h1>
            <p className="mt-1 max-w-[58ch] text-sm text-muted-foreground">
              Use an API key to authenticate requests to the HQ API from your server. Anyone who has it can act as
              you, so keep it secret.
            </p>
          </div>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus />
            New API key
          </Button>
        </div>

        <div className="mt-6 overflow-hidden rounded-none border border-border bg-card">
          {listError ? (
            <div className="px-4 py-10 text-center">
              <p className="text-sm text-destructive">{listError}</p>
              <Button variant="outline" size="sm" className="mt-3" onClick={() => reloadKeys()}>
                Try again
              </Button>
            </div>
          ) : pageData === null ? (
            <div className="flex items-center justify-center gap-2 px-4 py-16 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              Loading API keys…
            </div>
          ) : pageData.apiKeyList.length === 0 ? (
            <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
              <KeyRound className="size-8 text-muted-foreground" aria-hidden="true" />
              <div>
                <p className="text-sm font-medium">No API keys yet</p>
                <p className="text-sm text-muted-foreground">
                  Create one to start authenticating requests to the HQ API.
                </p>
              </div>
              <Button size="sm" onClick={() => setCreateOpen(true)}>
                <Plus />
                New API key
              </Button>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {pageData.apiKeyList.map((key) => {
                const status = keyStatus(key);
                return (
                  <div key={key.apiKeyId} className="flex flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
                    <div className="min-w-0 flex-1 basis-48">
                      <p className="truncate text-sm font-medium">{key.name}</p>
                      <p className="label-micro mt-0.5">Created {formatDate(key.createdAt)}</p>
                    </div>
                    <Badge variant={status.variant}>{status.label}</Badge>
                    <p className="text-sm whitespace-nowrap text-muted-foreground">{expiryLabel(key)}</p>
                    <div className="ml-auto flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Edit ${key.name}`}
                        onClick={() => setEditTarget(key)}
                      >
                        <Pencil />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Delete ${key.name}`}
                        onClick={() => setDeleteTarget(key)}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {pageData && pageData.numberOfPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <p className="text-sm text-muted-foreground">
              Page {pageData.pageNo + 1} of {pageData.numberOfPages} · {pageData.totalElements} key
              {pageData.totalElements === 1 ? "" : "s"}
            </p>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => reloadKeys(pageData.pageNo - 1)}
                disabled={pageData.pageNo === 0}
              >
                <ChevronLeft />
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => reloadKeys(pageData.pageNo + 1)}
                disabled={pageData.pageNo + 1 >= pageData.numberOfPages}
              >
                Next
                <ChevronRight />
              </Button>
            </div>
          </div>
        )}
      </main>

      <CreateKeyDialog open={createOpen} onOpenChange={setCreateOpen} onCreated={reloadKeys} />
      <EditKeyDialog apiKey={editTarget} onOpenChange={(open) => !open && setEditTarget(null)} onSaved={reloadKeys} />
      <DeleteKeyDialog
        apiKey={deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        onDeleted={reloadKeys}
      />
    </div>
  );
}

function CopyButton({ value, className }: { value: string; className?: string }) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <Button type="button" variant="outline" size="sm" onClick={handleCopy} className={className}>
      {copied ? <Check /> : <Copy />}
      {copied ? "Copied" : "Copy"}
    </Button>
  );
}

function CreateKeyDialog({
  open,
  onOpenChange,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: () => void;
}) {
  const [name, setName] = useState("");
  const [expiry, setExpiry] = useState<ApiKeyExpiry>("ONE_MONTH");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<ApiKeyCreated | null>(null);

  function reset() {
    setName("");
    setExpiry("ONE_MONTH");
    setSubmitting(false);
    setError(null);
    setCreated(null);
  }

  function handleOpenChange(next: boolean) {
    // A raw key was just issued - closing without reading it should still refresh the list
    // behind the dialog (it already exists server-side), but the value itself is gone for good.
    if (!next && created) onCreated();
    onOpenChange(next);
    if (!next) reset();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await createApiKey(name.trim(), expiry);
      setCreated(result);
      onCreated();
    } catch (err) {
      setError(errorMessage(err, "Couldn't create the API key."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        {created ? (
          <>
            <DialogHeader>
              <DialogTitle>Save your API key</DialogTitle>
              <DialogDescription>
                This is the only time it's shown. Store it somewhere safe — you'll need to create a new key if you
                lose it.
              </DialogDescription>
            </DialogHeader>
            <div className="flex flex-col gap-2 rounded-lg border border-border bg-muted/50 p-2.5">
              <code className="font-mono text-xs break-all">{created.rawKey}</code>
              <CopyButton value={created.rawKey} className="self-end" />
            </div>
            <DialogFooter>
              <Button type="button" onClick={() => handleOpenChange(false)}>
                Done
              </Button>
            </DialogFooter>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="contents">
            <DialogHeader>
              <DialogTitle>New API key</DialogTitle>
              <DialogDescription>Give it a name you'll recognize and choose when it should expire.</DialogDescription>
            </DialogHeader>
            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="apikey-name">Name</Label>
                <Input
                  id="apikey-name"
                  autoFocus
                  placeholder="e.g. Production server"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  disabled={submitting}
                  required
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="apikey-expiry">Expires</Label>
                <Select value={expiry} onValueChange={(v) => setExpiry(v as ApiKeyExpiry)} disabled={submitting}>
                  <SelectTrigger id="apikey-expiry">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {EXPIRY_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={submitting || name.trim().length === 0}>
                {submitting && <Loader2 className="animate-spin" />}
                Create key
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

function EditKeyDialog({
  apiKey,
  onOpenChange,
  onSaved,
}: {
  apiKey: ApiKey | null;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState("");
  const [expiry, setExpiry] = useState<ApiKeyExpiry>("ONE_MONTH");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (apiKey) {
      setName(apiKey.name);
      setExpiry("ONE_MONTH");
      setSubmitting(false);
      setError(null);
    }
  }, [apiKey]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!apiKey) return;
    setSubmitting(true);
    setError(null);
    try {
      await patchApiKey(apiKey.apiKeyId, { name: name.trim(), expiry });
      onSaved();
      onOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't save changes."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={apiKey !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="contents">
          <DialogHeader>
            <DialogTitle>Edit API key</DialogTitle>
            <DialogDescription>
              Renaming doesn't change the key itself. Changing the expiry extends it from today.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="apikey-edit-name">Name</Label>
              <Input
                id="apikey-edit-name"
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={submitting}
                required
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="apikey-edit-expiry">New expiry</Label>
              <Select value={expiry} onValueChange={(v) => setExpiry(v as ApiKeyExpiry)} disabled={submitting}>
                <SelectTrigger id="apikey-edit-expiry">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {EXPIRY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || name.trim().length === 0}>
              {submitting && <Loader2 className="animate-spin" />}
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function DeleteKeyDialog({
  apiKey,
  onOpenChange,
  onDeleted,
}: {
  apiKey: ApiKey | null;
  onOpenChange: (open: boolean) => void;
  onDeleted: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (apiKey) {
      setSubmitting(false);
      setError(null);
    }
  }, [apiKey]);

  async function handleDelete() {
    if (!apiKey) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteApiKey(apiKey.apiKeyId);
      onDeleted();
      onOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't delete the API key."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={apiKey !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete API key</DialogTitle>
          <DialogDescription>
            {apiKey && (
              <>
                <span className="font-medium text-foreground">{apiKey.name}</span> will stop working immediately.
                This can't be undone.
              </>
            )}
          </DialogDescription>
        </DialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" variant="destructive" onClick={handleDelete} disabled={submitting}>
            {submitting && <Loader2 className="animate-spin" />}
            Delete
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
