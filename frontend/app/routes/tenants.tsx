import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Building2, ChevronLeft, ChevronRight, Loader2, Pencil, Plus, Trash2, Webhook, X } from "lucide-react";

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
import {
  addChannelEventType,
  ApiRequestError,
  createChannel,
  createTenant,
  deleteChannel,
  deleteTenant,
  fetchProfile,
  getTenant,
  listChannelEventTypes,
  listChannels,
  listTenants,
  patchChannel,
  patchTenant,
  removeChannelEventType,
  type Channel,
  type ChannelPage,
  type Tenant,
  type TenantPage,
} from "~/lib/api";

type AuthState = "loading" | "ready" | "redirecting";

/** Best-effort message for a failed mutation - falls back for network errors, which carry no ApiError. */
function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiRequestError) return err.apiError?.message ?? fallback;
  return fallback;
}

export default function TenantsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedTenantId = searchParams.get("tenant");

  const [authState, setAuthState] = useState<AuthState>("loading");

  const [tenantsPage, setTenantsPage] = useState<TenantPage | null>(null);
  const [tenantsError, setTenantsError] = useState<string | null>(null);

  const [selectedTenant, setSelectedTenant] = useState<Tenant | null>(null);
  const [selectedTenantError, setSelectedTenantError] = useState<string | null>(null);

  const [channelsPage, setChannelsPage] = useState<ChannelPage | null>(null);
  const [channelsError, setChannelsError] = useState<string | null>(null);

  const [createTenantOpen, setCreateTenantOpen] = useState(false);
  const [editTenantTarget, setEditTenantTarget] = useState<Tenant | null>(null);
  const [deleteTenantTarget, setDeleteTenantTarget] = useState<Tenant | null>(null);

  const [createChannelOpen, setCreateChannelOpen] = useState(false);
  const [editChannelTarget, setEditChannelTarget] = useState<Channel | null>(null);
  const [deleteChannelTarget, setDeleteChannelTarget] = useState<Channel | null>(null);

  function selectTenant(tenantId: string | null) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (tenantId) next.set("tenant", tenantId);
        else next.delete("tenant");
        return next;
      },
      { replace: true }
    );
  }

  // Sign in, then load the tenant list.
  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    // Tracks whether the profile fetch itself succeeded - not React state, since the closure
    // below would otherwise see the state value from the render that started this effect, not
    // whatever setAuthState("ready") set it to a moment ago.
    let profileConfirmed = false;

    fetchProfile(controller.signal)
      .then(() => {
        if (cancelled) return;
        profileConfirmed = true;
        setAuthState("ready");
        return listTenants(0, 20, controller.signal);
      })
      .then((fetched) => {
        if (!cancelled && fetched) setTenantsPage(fetched);
      })
      .catch((err) => {
        if (cancelled) return;
        if (!profileConfirmed) {
          // Failed before we ever confirmed a session - treat like the dashboard does.
          setAuthState("redirecting");
          navigate("/login", { replace: true });
          return;
        }
        setTenantsError(errorMessage(err, "Couldn't load tenants."));
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [navigate]);

  // Resolve the selected tenant from the URL. Skips the fetch when a list-row click already
  // put the full Tenant in state, so picking a tenant feels instant.
  useEffect(() => {
    if (authState !== "ready") return;
    if (!selectedTenantId) {
      setSelectedTenant(null);
      setSelectedTenantError(null);
      return;
    }
    if (selectedTenant?.tenantId === selectedTenantId) return;

    let cancelled = false;
    const controller = new AbortController();
    setSelectedTenant(null);
    setSelectedTenantError(null);
    getTenant(selectedTenantId, controller.signal)
      .then((tenant) => {
        if (!cancelled) setSelectedTenant(tenant);
      })
      .catch((err) => {
        if (!cancelled) setSelectedTenantError(errorMessage(err, "Couldn't load this tenant."));
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [authState, selectedTenantId, selectedTenant]);

  // Load channels for whichever tenant is selected.
  useEffect(() => {
    if (!selectedTenant) {
      setChannelsPage(null);
      setChannelsError(null);
      return;
    }
    let cancelled = false;
    const controller = new AbortController();
    setChannelsPage(null);
    setChannelsError(null);
    listChannels(selectedTenant.tenantId, 0, 20, controller.signal)
      .then((fetched) => {
        if (!cancelled) setChannelsPage(fetched);
      })
      .catch((err) => {
        if (!cancelled) setChannelsError(errorMessage(err, "Couldn't load channels."));
      });
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [selectedTenant]);

  async function reloadTenants(page = tenantsPage?.pageNo ?? 0) {
    try {
      const fetched = await listTenants(page);
      // Deleting the last tenant on a page beyond the first empties it - fall back a page
      // instead of showing a dead end.
      if (fetched.tenantList.length === 0 && fetched.pageNo > 0) {
        return reloadTenants(fetched.pageNo - 1);
      }
      setTenantsPage(fetched);
      setTenantsError(null);
    } catch (err) {
      setTenantsError(errorMessage(err, "Couldn't load tenants."));
    }
  }

  async function reloadChannels(page = channelsPage?.pageNo ?? 0) {
    if (!selectedTenant) return;
    try {
      const fetched = await listChannels(selectedTenant.tenantId, page);
      if (fetched.channelList.length === 0 && fetched.pageNo > 0) {
        return reloadChannels(fetched.pageNo - 1);
      }
      setChannelsPage(fetched);
      setChannelsError(null);
    } catch (err) {
      setChannelsError(errorMessage(err, "Couldn't load channels."));
    }
  }

  if (authState !== "ready") {
    return (
      <div className="flex min-h-svh flex-col bg-background">
        <AppHeader />
        <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-10">
          <p className="text-sm text-muted-foreground">Loading…</p>
        </main>
      </div>
    );
  }

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <AppHeader />
      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col md:flex-row">
        <aside className="shrink-0 border-b border-border md:w-72 md:border-r md:border-b-0">
          <div className="flex items-center justify-between gap-2 px-6 py-5">
            <h1 className="font-display text-lg font-extrabold tracking-tight">Tenants</h1>
            <Button variant="ghost" size="icon-sm" aria-label="New tenant" onClick={() => setCreateTenantOpen(true)}>
              <Plus />
            </Button>
          </div>

          {tenantsError ? (
            <div className="px-6 py-8 text-center">
              <p className="text-sm text-destructive">{tenantsError}</p>
              <Button variant="outline" size="sm" className="mt-3" onClick={() => reloadTenants()}>
                Try again
              </Button>
            </div>
          ) : tenantsPage === null ? (
            <div className="flex items-center justify-center gap-2 px-6 py-12 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              Loading…
            </div>
          ) : tenantsPage.tenantList.length === 0 ? (
            <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
              <Building2 className="size-7 text-muted-foreground" aria-hidden="true" />
              <div>
                <p className="text-sm font-medium">No tenants yet</p>
                <p className="text-sm text-muted-foreground">Create one to start organizing your resources.</p>
              </div>
              <Button size="sm" onClick={() => setCreateTenantOpen(true)}>
                <Plus />
                New tenant
              </Button>
            </div>
          ) : (
            <div className="divide-y divide-border border-t border-border">
              {tenantsPage.tenantList.map((tenant) => {
                const active = tenant.tenantId === selectedTenant?.tenantId;
                return (
                  <button
                    key={tenant.tenantId}
                    type="button"
                    onClick={() => {
                      setSelectedTenant(tenant);
                      selectTenant(tenant.tenantId);
                    }}
                    aria-current={active ? "true" : undefined}
                    className={
                      "flex w-full items-center gap-2 border-l-2 px-6 py-3 text-left text-sm transition-colors " +
                      (active
                        ? "border-l-primary bg-muted font-medium text-foreground"
                        : "border-l-transparent text-muted-foreground hover:bg-muted/50 hover:text-foreground")
                    }
                  >
                    <span className="truncate">{tenant.name}</span>
                  </button>
                );
              })}
            </div>
          )}

          {tenantsPage && tenantsPage.numberOfPages > 1 && (
            <div className="flex items-center justify-between gap-2 border-t border-border px-6 py-3">
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Previous page"
                onClick={() => reloadTenants(tenantsPage.pageNo - 1)}
                disabled={tenantsPage.pageNo === 0}
              >
                <ChevronLeft />
              </Button>
              <p className="text-xs text-muted-foreground">
                {tenantsPage.pageNo + 1} / {tenantsPage.numberOfPages}
              </p>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Next page"
                onClick={() => reloadTenants(tenantsPage.pageNo + 1)}
                disabled={tenantsPage.pageNo + 1 >= tenantsPage.numberOfPages}
              >
                <ChevronRight />
              </Button>
            </div>
          )}
        </aside>

        <section className="min-w-0 flex-1 px-6 py-10">
          {selectedTenantError ? (
            <div className="px-4 py-10 text-center">
              <p className="text-sm text-destructive">{selectedTenantError}</p>
              <Button variant="outline" size="sm" className="mt-3" onClick={() => selectTenant(null)}>
                Back to tenants
              </Button>
            </div>
          ) : !selectedTenantId ? (
            <div className="flex h-full flex-col items-center justify-center gap-2 py-16 text-center">
              <Building2 className="size-8 text-muted-foreground" aria-hidden="true" />
              <p className="text-sm font-medium">No tenant selected</p>
              <p className="max-w-[36ch] text-sm text-muted-foreground">
                Pick a tenant on the left to see its channels, or create a new one.
              </p>
            </div>
          ) : selectedTenant === null ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              Loading tenant…
            </div>
          ) : (
            <>
              <div className="flex flex-wrap items-start justify-between gap-4">
                <h2 className="font-display text-2xl font-extrabold tracking-tight">{selectedTenant.name}</h2>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Edit ${selectedTenant.name}`}
                    onClick={() => setEditTenantTarget(selectedTenant)}
                  >
                    <Pencil />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Delete ${selectedTenant.name}`}
                    onClick={() => setDeleteTenantTarget(selectedTenant)}
                  >
                    <Trash2 />
                  </Button>
                </div>
              </div>

              <div className="mt-10 flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h3 className="text-sm font-semibold">Channels</h3>
                  <p className="mt-1 max-w-[52ch] text-sm text-muted-foreground">
                    Webhook endpoints this tenant's events are delivered to.
                  </p>
                </div>
                <Button size="sm" onClick={() => setCreateChannelOpen(true)}>
                  <Plus />
                  New channel
                </Button>
              </div>

              <div className="mt-4 overflow-hidden rounded-none border border-border bg-card">
                {channelsError ? (
                  <div className="px-4 py-10 text-center">
                    <p className="text-sm text-destructive">{channelsError}</p>
                    <Button variant="outline" size="sm" className="mt-3" onClick={() => reloadChannels()}>
                      Try again
                    </Button>
                  </div>
                ) : channelsPage === null ? (
                  <div className="flex items-center justify-center gap-2 px-4 py-14 text-sm text-muted-foreground">
                    <Loader2 className="size-4 animate-spin" />
                    Loading channels…
                  </div>
                ) : channelsPage.channelList.length === 0 ? (
                  <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
                    <Webhook className="size-7 text-muted-foreground" aria-hidden="true" />
                    <div>
                      <p className="text-sm font-medium">No channels yet</p>
                      <p className="text-sm text-muted-foreground">
                        Create one to start delivering events to a webhook.
                      </p>
                    </div>
                    <Button size="sm" onClick={() => setCreateChannelOpen(true)}>
                      <Plus />
                      New channel
                    </Button>
                  </div>
                ) : (
                  <div className="divide-y divide-border">
                    {channelsPage.channelList.map((channel) => (
                      <ChannelRow
                        key={channel.channelId}
                        tenantId={selectedTenant.tenantId}
                        channel={channel}
                        onEdit={() => setEditChannelTarget(channel)}
                        onDelete={() => setDeleteChannelTarget(channel)}
                      />
                    ))}
                  </div>
                )}
              </div>

              {channelsPage && channelsPage.numberOfPages > 1 && (
                <div className="mt-4 flex items-center justify-between">
                  <p className="text-sm text-muted-foreground">
                    Page {channelsPage.pageNo + 1} of {channelsPage.numberOfPages} · {channelsPage.totalElements}{" "}
                    channel
                    {channelsPage.totalElements === 1 ? "" : "s"}
                  </p>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => reloadChannels(channelsPage.pageNo - 1)}
                      disabled={channelsPage.pageNo === 0}
                    >
                      <ChevronLeft />
                      Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => reloadChannels(channelsPage.pageNo + 1)}
                      disabled={channelsPage.pageNo + 1 >= channelsPage.numberOfPages}
                    >
                      Next
                      <ChevronRight />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </section>
      </main>

      <CreateTenantDialog
        open={createTenantOpen}
        onOpenChange={setCreateTenantOpen}
        onCreated={(tenant) => {
          reloadTenants(0);
          setSelectedTenant(tenant);
          selectTenant(tenant.tenantId);
        }}
      />
      <EditTenantDialog
        tenant={editTenantTarget}
        onOpenChange={(open) => !open && setEditTenantTarget(null)}
        onSaved={(tenant) => {
          reloadTenants();
          if (selectedTenant?.tenantId === tenant.tenantId) setSelectedTenant(tenant);
        }}
      />
      <DeleteTenantDialog
        tenant={deleteTenantTarget}
        onOpenChange={(open) => !open && setDeleteTenantTarget(null)}
        onDeleted={(tenant) => {
          reloadTenants();
          if (selectedTenant?.tenantId === tenant.tenantId) selectTenant(null);
        }}
      />

      {selectedTenant && (
        <>
          <CreateChannelDialog
            tenantId={selectedTenant.tenantId}
            open={createChannelOpen}
            onOpenChange={setCreateChannelOpen}
            onCreated={() => reloadChannels(0)}
          />
          <EditChannelDialog
            tenantId={selectedTenant.tenantId}
            channel={editChannelTarget}
            onOpenChange={(open) => !open && setEditChannelTarget(null)}
            onSaved={() => reloadChannels()}
          />
          <DeleteChannelDialog
            tenantId={selectedTenant.tenantId}
            channel={deleteChannelTarget}
            onOpenChange={(open) => !open && setDeleteChannelTarget(null)}
            onDeleted={() => reloadChannels()}
          />
        </>
      )}
    </div>
  );
}

function CreateTenantDialog({
  open,
  onOpenChange,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (tenant: Tenant) => void;
}) {
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setName("");
    setSubmitting(false);
    setError(null);
  }

  function handleOpenChange(next: boolean) {
    onOpenChange(next);
    if (!next) reset();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const tenant = await createTenant(name.trim());
      onCreated(tenant);
      handleOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't create the tenant."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="contents">
          <DialogHeader>
            <DialogTitle>New tenant</DialogTitle>
            <DialogDescription>Give it a name you'll recognize.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="tenant-name">Name</Label>
              <Input
                id="tenant-name"
                autoFocus
                placeholder="e.g. Acme Merchant"
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={submitting}
                required
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || name.trim().length === 0}>
              {submitting && <Loader2 className="animate-spin" />}
              Create tenant
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function EditTenantDialog({
  tenant,
  onOpenChange,
  onSaved,
}: {
  tenant: Tenant | null;
  onOpenChange: (open: boolean) => void;
  onSaved: (tenant: Tenant) => void;
}) {
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (tenant) {
      setName(tenant.name);
      setSubmitting(false);
      setError(null);
    }
  }, [tenant]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!tenant) return;
    setSubmitting(true);
    setError(null);
    try {
      const saved = await patchTenant(tenant.tenantId, { name: name.trim() });
      onSaved(saved);
      onOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't save changes."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={tenant !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="contents">
          <DialogHeader>
            <DialogTitle>Edit tenant</DialogTitle>
            <DialogDescription>Rename this tenant.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="tenant-edit-name">Name</Label>
              <Input
                id="tenant-edit-name"
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={submitting}
                required
              />
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

function DeleteTenantDialog({
  tenant,
  onOpenChange,
  onDeleted,
}: {
  tenant: Tenant | null;
  onOpenChange: (open: boolean) => void;
  onDeleted: (tenant: Tenant) => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (tenant) {
      setSubmitting(false);
      setError(null);
    }
  }, [tenant]);

  async function handleDelete() {
    if (!tenant) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteTenant(tenant.tenantId);
      onDeleted(tenant);
      onOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't delete the tenant."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={tenant !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete tenant</DialogTitle>
          <DialogDescription>
            {tenant && (
              <>
                <span className="font-medium text-foreground">{tenant.name}</span> and its channels will be
                permanently deleted. This can't be undone.
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

function CreateChannelDialog({
  tenantId,
  open,
  onOpenChange,
  onCreated,
}: {
  tenantId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: () => void;
}) {
  const [callBackUrl, setCallBackUrl] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setCallBackUrl("");
    setSubmitting(false);
    setError(null);
  }

  function handleOpenChange(next: boolean) {
    onOpenChange(next);
    if (!next) reset();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await createChannel(tenantId, callBackUrl.trim());
      onCreated();
      handleOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't create the channel."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="contents">
          <DialogHeader>
            <DialogTitle>New channel</DialogTitle>
            <DialogDescription>Events for this tenant will be POSTed to this URL.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="channel-url">Callback URL</Label>
              <Input
                id="channel-url"
                type="url"
                autoFocus
                placeholder="https://example.com/webhooks/hq"
                value={callBackUrl}
                onChange={(e) => setCallBackUrl(e.target.value)}
                disabled={submitting}
                required
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || callBackUrl.trim().length === 0}>
              {submitting && <Loader2 className="animate-spin" />}
              Create channel
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function EditChannelDialog({
  tenantId,
  channel,
  onOpenChange,
  onSaved,
}: {
  tenantId: string;
  channel: Channel | null;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}) {
  const [callBackUrl, setCallBackUrl] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (channel) {
      setCallBackUrl(channel.callBackUrl);
      setSubmitting(false);
      setError(null);
    }
  }, [channel]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!channel) return;
    setSubmitting(true);
    setError(null);
    try {
      await patchChannel(tenantId, channel.channelId, { callBackUrl: callBackUrl.trim() });
      onSaved();
      onOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't save changes."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={channel !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit} className="contents">
          <DialogHeader>
            <DialogTitle>Edit channel</DialogTitle>
            <DialogDescription>Update the callback URL.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="channel-edit-url">Callback URL</Label>
              <Input
                id="channel-edit-url"
                type="url"
                autoFocus
                value={callBackUrl}
                onChange={(e) => setCallBackUrl(e.target.value)}
                disabled={submitting}
                required
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || callBackUrl.trim().length === 0}>
              {submitting && <Loader2 className="animate-spin" />}
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function DeleteChannelDialog({
  tenantId,
  channel,
  onOpenChange,
  onDeleted,
}: {
  tenantId: string;
  channel: Channel | null;
  onOpenChange: (open: boolean) => void;
  onDeleted: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (channel) {
      setSubmitting(false);
      setError(null);
    }
  }, [channel]);

  async function handleDelete() {
    if (!channel) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteChannel(tenantId, channel.channelId);
      onDeleted();
      onOpenChange(false);
    } catch (err) {
      setError(errorMessage(err, "Couldn't delete the channel."));
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={channel !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete channel</DialogTitle>
          <DialogDescription>
            {channel && (
              <>
                <span className="font-medium text-foreground">{channel.callBackUrl}</span> will be permanently
                deleted. This can't be undone.
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

/** A channel row with its event types shown and editable inline - no dialog needed to see or change what a channel receives. */
function ChannelRow({
  tenantId,
  channel,
  onEdit,
  onDelete,
}: {
  tenantId: string;
  channel: Channel;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const [eventTypes, setEventTypes] = useState<string[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [newEventType, setNewEventType] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    setEventTypes(null);
    setLoadError(null);
    listChannelEventTypes(tenantId, channel.channelId, controller.signal)
      .then((fetched) => {
        if (!cancelled) setEventTypes(fetched.map((e) => e.eventType));
      })
      .catch((err) => {
        if (!cancelled) setLoadError(errorMessage(err, "Couldn't load event types."));
      });
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [tenantId, channel.channelId]);

  async function handleAdd(e: FormEvent) {
    e.preventDefault();
    const value = newEventType.trim();
    if (!value) return;
    setSubmitting(true);
    setAddError(null);
    try {
      await addChannelEventType(tenantId, channel.channelId, value);
      setEventTypes((prev) => (prev ? [...prev, value] : [value]));
      setNewEventType("");
    } catch (err) {
      setAddError(errorMessage(err, "Couldn't add the event type."));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRemove(eventType: string) {
    setRemoving(eventType);
    setAddError(null);
    try {
      await removeChannelEventType(tenantId, channel.channelId, eventType);
      setEventTypes((prev) => (prev ? prev.filter((e) => e !== eventType) : prev));
    } catch (err) {
      setAddError(errorMessage(err, "Couldn't remove the event type."));
    } finally {
      setRemoving(null);
    }
  }

  return (
    <div className="flex flex-col gap-2 px-4 py-3">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
        <div className="min-w-0 flex-1 basis-64">
          <p className="truncate text-sm font-medium">{channel.callBackUrl}</p>
        </div>
        <div className="ml-auto flex items-center gap-1">
          <Button variant="ghost" size="icon-sm" aria-label={`Edit ${channel.callBackUrl}`} onClick={onEdit}>
            <Pencil />
          </Button>
          <Button variant="ghost" size="icon-sm" aria-label={`Delete ${channel.callBackUrl}`} onClick={onDelete}>
            <Trash2 />
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        {loadError ? (
          <p className="text-xs text-destructive">{loadError}</p>
        ) : eventTypes === null ? (
          <Loader2 className="size-3.5 animate-spin text-muted-foreground" />
        ) : (
          <>
            {eventTypes.length === 0 && (
              <span className="text-xs text-muted-foreground">No event types - this channel receives nothing</span>
            )}
            {eventTypes.map((eventType) => (
              <Badge key={eventType} variant="neutral" className="pr-1">
                {eventType}
                <button
                  type="button"
                  aria-label={`Remove ${eventType}`}
                  onClick={() => handleRemove(eventType)}
                  disabled={removing === eventType}
                  className="ml-0.5 rounded-full p-0.5 hover:bg-foreground/10"
                >
                  {removing === eventType ? <Loader2 className="size-3 animate-spin" /> : <X className="size-3" />}
                </button>
              </Badge>
            ))}
            <form onSubmit={handleAdd} className="flex items-center gap-1">
              <Input
                aria-label="Add event type"
                placeholder="Add event type…"
                value={newEventType}
                onChange={(e) => setNewEventType(e.target.value)}
                disabled={submitting}
                className="h-6 w-36 rounded-full px-2.5 text-xs"
              />
              <Button
                type="submit"
                size="icon-xs"
                variant="ghost"
                aria-label="Add"
                disabled={submitting || newEventType.trim().length === 0}
              >
                {submitting ? <Loader2 className="size-3 animate-spin" /> : <Plus className="size-3" />}
              </Button>
            </form>
          </>
        )}
      </div>
      {addError && <p className="text-xs text-destructive">{addError}</p>}
    </div>
  );
}
