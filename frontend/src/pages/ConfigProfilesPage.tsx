import * as React from "react";
import { Copy, Download, FileJson, RotateCcw, Save, ShieldCheck, Trash2, Upload } from "lucide-react";
import { api } from "../lib/api";
import type { ConfigProfileDetail, ConfigProfileDiffEntry, ConfigProfileList, ConfigProfileSummary, ServerView } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { ServerSelect } from "../components/ServerSelect";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { ConfirmDialog } from "../components/ui/confirm-dialog";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";

type PendingAction =
  | { type: "apply"; profile: ConfigProfileSummary }
  | { type: "delete"; profile: ConfigProfileSummary }
  | { type: "restore" };

const columns = (
  selectProfile: (profile: ConfigProfileSummary) => void,
  setPending: (pending: PendingAction) => void,
  duplicateProfile: (profile: ConfigProfileSummary) => void,
  exportProfile: (profile: ConfigProfileSummary) => void
): DataTableColumn<ConfigProfileSummary>[] => [
  {
    key: "name",
    header: "Perfil",
    sortable: true,
    searchValue: (profile) => `${profile.name} ${profile.description}`,
    render: (profile) => (
      <button className="text-left font-medium text-primary hover:underline" onClick={() => selectProfile(profile)}>
        {profile.name}
      </button>
    )
  },
  { key: "description", header: "Descripcion", sortable: true, searchValue: (profile) => profile.description, render: (profile) => <span className="text-muted-foreground">{profile.description || "-"}</span> },
  {
    key: "state",
    header: "Estado",
    sortable: true,
    searchValue: (profile) => `${profile.active ? "activo" : ""} ${profile.isDefault ? "default" : ""}`,
    render: (profile) => (
      <div className="flex flex-wrap gap-2">
        {profile.active ? <span className="rounded-md border border-emerald-400/30 bg-emerald-400/10 px-2 py-1 text-xs text-emerald-200">Activo</span> : null}
        {profile.isDefault ? <span className="rounded-md border border-cyan-400/30 bg-cyan-400/10 px-2 py-1 text-xs text-cyan-200">Default</span> : null}
      </div>
    )
  },
  { key: "parameters", header: "Parametros", sortable: true, sortValue: (profile) => profile.parameterCount, render: (profile) => profile.parameterCount },
  { key: "updatedAt", header: "Modificado", sortable: true, searchValue: (profile) => profile.updatedAt, render: (profile) => <span className="text-muted-foreground">{formatDate(profile.updatedAt)}</span> },
  {
    key: "actions",
    header: "Acciones",
    className: "text-right",
    render: (profile) => (
      <div className="flex flex-wrap justify-end gap-2">
        <Button size="sm" variant="outline" onClick={() => setPending({ type: "apply", profile })}><ShieldCheck className="h-3.5 w-3.5" />Aplicar</Button>
        <Button size="sm" variant="outline" onClick={() => duplicateProfile(profile)}><Copy className="h-3.5 w-3.5" />Duplicar</Button>
        <Button size="sm" variant="outline" onClick={() => exportProfile(profile)}><Download className="h-3.5 w-3.5" />Exportar</Button>
        <Button size="sm" variant="destructive" disabled={profile.isDefault || profile.active} onClick={() => setPending({ type: "delete", profile })}><Trash2 className="h-3.5 w-3.5" />Eliminar</Button>
      </div>
    )
  }
];

export function ConfigProfilesPage({
  selectedServerId,
  onSelectServer
}: {
  selectedServerId: number | null;
  onSelectServer: (id: number) => void;
}) {
  const [servers, setServers] = React.useState<ServerView[] | null>(null);
  const [list, setList] = React.useState<ConfigProfileList | null>(null);
  const [detail, setDetail] = React.useState<ConfigProfileDetail | null>(null);
  const [selectedProfileId, setSelectedProfileId] = React.useState<string | null>(null);
  const [diff, setDiff] = React.useState<ConfigProfileDiffEntry[]>([]);
  const [pending, setPending] = React.useState<PendingAction | null>(null);
  const [busy, setBusy] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    api.servers().then((items) => {
      setServers(items);
      if (!selectedServerId && items[0]) onSelectServer(items[0].id);
    }).catch((error) => toast({ title: "No se pudieron cargar servidores", description: error.message, variant: "error" }));
  }, [onSelectServer, selectedServerId, toast]);

  const loadProfiles = React.useCallback(async (preferredProfileId?: string | null) => {
    if (!selectedServerId) return;
    const next = await api.configProfiles(selectedServerId);
    setList(next);
    const selected = preferredProfileId && next.profiles.some((profile) => profile.id === preferredProfileId) ? preferredProfileId : next.activeProfileId || next.defaultProfileId;
    setSelectedProfileId(selected);
    if (selected) {
      const loaded = await api.configProfile(selectedServerId, selected);
      setDetail(loaded);
      setDiff(await api.configProfileDiff(selectedServerId, selected));
    }
  }, [selectedServerId]);

  React.useEffect(() => {
    setList(null);
    setDetail(null);
    setDiff([]);
    setSelectedProfileId(null);
    loadProfiles(null).catch((error) => toast({ title: "No se pudieron cargar perfiles", description: error.message, variant: "error" }));
  }, [loadProfiles, selectedServerId, toast]);

  const selectProfile = async (profile: ConfigProfileSummary) => {
    if (!selectedServerId) return;
    setSelectedProfileId(profile.id);
    setDetail(await api.configProfile(selectedServerId, profile.id));
    setDiff(await api.configProfileDiff(selectedServerId, profile.id));
  };

  const createProfile = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedServerId) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const created = await api.createConfigProfile(selectedServerId, {
        name: String(form.get("name") || ""),
        description: String(form.get("description") || "")
      });
      event.currentTarget.reset();
      setSelectedProfileId(created.id);
      setDetail(created);
      toast({ title: "Perfil creado", description: "Se guardo desde la configuracion activa." });
      await loadProfiles(created.id);
    } catch (error) {
      toast({ title: "No se pudo crear", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const saveDetail = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedServerId || !detail) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const saved = await api.updateConfigProfile(selectedServerId, detail.id, {
        name: String(form.get("name") || detail.name),
        description: String(form.get("description") || ""),
        configuration: String(form.get("configuration") || detail.configuration)
      });
      setSelectedProfileId(saved.id);
      setDetail(saved);
      setDiff(await api.configProfileDiff(selectedServerId, saved.id));
      toast({ title: "Perfil guardado", description: "Los cambios quedaron persistidos." });
      await loadProfiles(saved.id);
    } catch (error) {
      toast({ title: "No se pudo guardar", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const duplicateProfile = async (profile: ConfigProfileSummary) => {
    if (!selectedServerId) return;
    const name = `${profile.name} copia`;
    setBusy(true);
    try {
      const copy = await api.duplicateConfigProfile(selectedServerId, profile.id, { name, description: profile.description });
      setSelectedProfileId(copy.id);
      setDetail(copy);
      toast({ title: "Perfil duplicado", description: name });
      await loadProfiles(copy.id);
    } catch (error) {
      toast({ title: "No se pudo duplicar", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const exportProfile = async (profile: ConfigProfileSummary) => {
    if (!selectedServerId) return;
    const exported = await api.exportConfigProfile(selectedServerId, profile.id);
    const blob = new Blob([JSON.stringify(exported, null, 2)], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${profile.id}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
  };

  const importProfile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    if (!selectedServerId || !event.target.files?.[0]) return;
    setBusy(true);
    try {
      const text = await event.target.files[0].text();
      const imported = await api.importConfigProfile(selectedServerId, text);
      setSelectedProfileId(imported.id);
      setDetail(imported);
      toast({ title: "Perfil importado", description: imported.name });
      await loadProfiles(imported.id);
    } catch (error) {
      toast({ title: "No se pudo importar", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      event.target.value = "";
      setBusy(false);
    }
  };

  const confirmPending = async () => {
    if (!selectedServerId || !pending) return;
    setBusy(true);
    try {
      if (pending.type === "delete") {
        await api.deleteConfigProfile(selectedServerId, pending.profile.id);
        toast({ title: "Perfil eliminado", description: pending.profile.name });
      } else if (pending.type === "restore") {
        const result = await api.restoreDefaultConfigProfile(selectedServerId);
        toast({ title: "Default restaurado", description: result.message });
      } else {
        const result = await api.applyConfigProfile(selectedServerId, pending.profile.id);
        toast({ title: "Perfil aplicado", description: result.message });
      }
      setPending(null);
      await loadProfiles(pending.type === "delete" ? null : pending.type === "restore" ? "default" : pending.profile.id);
    } catch (error) {
      toast({ title: "La operacion fallo", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const pendingDescription = pending?.type === "delete"
    ? `Se eliminara ${pending.profile.name}. Esta accion no aplica al perfil default ni al activo.`
    : pending?.type === "restore"
      ? "Se aplicara el perfil default. Se creara un respaldo antes de escribir el archivo activo."
      : pending
        ? `Se aplicara ${pending.profile.name}. Se creara un respaldo y Palworld debera reiniciarse despues.`
        : "";

  return (
    <div>
      <SectionHeader title="Perfiles de configuracion" description="Configuraciones completas reutilizables por servidor, con default protegido y respaldos antes de aplicar." />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.1fr)_minmax(360px,0.9fr)]">
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Servidor</CardTitle>
              <CardDescription>Los perfiles se guardan separados para cada servidor registrado.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {servers ? <ServerSelect servers={servers} value={selectedServerId} onChange={onSelectServer} /> : <Skeleton className="h-10" />}
              {list?.externalModified ? (
                <div className="rounded-md border border-amber-400/30 bg-amber-400/10 p-3 text-sm text-amber-100">
                  Configuracion modificada fuera del perfil activo.
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Perfiles disponibles</CardTitle>
              <CardDescription>El perfil default se crea una sola vez desde el INI activo y no puede eliminarse.</CardDescription>
            </CardHeader>
            <CardContent>
              {!list ? (
                <div className="space-y-3"><Skeleton className="h-12" /><Skeleton className="h-12" /></div>
              ) : list.profiles.length === 0 ? (
                <EmptyState title="Sin perfiles" description="Al cargar el servidor se debe crear default automaticamente." />
              ) : (
                <DataTable
                  data={list.profiles}
                  columns={columns(selectProfile, setPending, duplicateProfile, exportProfile)}
                  getRowKey={(profile) => profile.id}
                  searchPlaceholder="Filtrar perfiles"
                  minWidth="980px"
                />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Crear desde configuracion activa</CardTitle>
              <CardDescription>Guarda una copia completa del PalWorldSettings.ini actual, sin incluir secretos.</CardDescription>
            </CardHeader>
            <CardContent>
              <form className="grid gap-3 md:grid-cols-[1fr_1.4fr_auto]" onSubmit={createProfile}>
                <input name="name" className="focus-ring h-10 rounded-md border border-input bg-background px-3" placeholder="Nombre del perfil" required />
                <input name="description" className="focus-ring h-10 rounded-md border border-input bg-background px-3" placeholder="Descripcion" />
                <Button type="submit" disabled={busy || !selectedServerId}><FileJson className="h-4 w-4" />Crear</Button>
              </form>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Editor de perfil</CardTitle>
              <CardDescription>Edita nombre, descripcion y contenido completo del perfil seleccionado.</CardDescription>
            </CardHeader>
            <CardContent>
              {!detail ? (
                <EmptyState title="Selecciona un perfil" description="Elige un registro de la tabla para ver su configuracion." />
              ) : (
                <form key={detail.id} className="space-y-4" onSubmit={saveDetail}>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="text-sm">
                      Nombre
                      <input name="name" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" defaultValue={detail.name} required />
                    </label>
                    <label className="text-sm">
                      Parametros
                      <input className="mt-1 h-10 w-full rounded-md border border-input bg-muted px-3 text-muted-foreground" value={detail.parameterCount} readOnly />
                    </label>
                  </div>
                  <label className="block text-sm">
                    Descripcion
                    <textarea name="description" className="focus-ring mt-1 h-20 w-full rounded-md border border-input bg-background px-3 py-2" defaultValue={detail.description} />
                  </label>
                  <label className="block text-sm">
                    Configuracion del perfil
                    <textarea name="configuration" className="focus-ring mt-1 h-72 w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs" defaultValue={detail.configuration} spellCheck={false} />
                  </label>
                  <div className="flex flex-wrap justify-between gap-2">
                    <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-md border border-border px-4 text-sm hover:bg-accent">
                      <Upload className="h-4 w-4" />
                      Importar JSON
                      <input type="file" accept="application/json,.json" className="hidden" onChange={importProfile} />
                    </label>
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" variant="outline" disabled={busy || !list?.defaultProfileId} onClick={() => setPending({ type: "restore" })}><RotateCcw className="h-4 w-4" />Restaurar default</Button>
                      <Button type="submit" disabled={busy}><Save className="h-4 w-4" />Guardar perfil</Button>
                    </div>
                  </div>
                </form>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Resumen de cambios</CardTitle>
              <CardDescription>Diferencias entre el INI activo y el perfil seleccionado.</CardDescription>
            </CardHeader>
            <CardContent>
              {diff.length === 0 ? (
                <EmptyState title="Sin diferencias" description="El perfil seleccionado coincide con la configuracion activa o aun no hay comparacion." />
              ) : (
                <div className="max-h-72 overflow-auto rounded-md border border-border">
                  <table className="w-full min-w-[520px] text-sm">
                    <thead className="sticky top-0 bg-card text-left text-muted-foreground">
                      <tr><th className="p-3">Campo</th><th className="p-3">Actual</th><th className="p-3">Perfil</th></tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {diff.slice(0, 100).map((item) => (
                        <tr key={item.key}>
                          <td className="p-3 font-medium">{item.key}</td>
                          <td className="p-3 text-muted-foreground">{item.previousValue ?? "-"}</td>
                          <td className="p-3 text-muted-foreground">{item.newValue ?? "-"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={Boolean(pending)}
        busy={busy}
        title="Confirmar perfiles"
        description={pendingDescription}
        confirmLabel={pending?.type === "delete" ? "Eliminar" : "Aplicar"}
        onCancel={() => setPending(null)}
        onConfirm={confirmPending}
      />
    </div>
  );
}

function formatDate(value: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(date);
}
