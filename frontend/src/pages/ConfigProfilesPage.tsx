import * as React from "react";
import { ArrowLeft, Copy, Download, FileJson, Pencil, RotateCcw, Save, ShieldCheck, Star, Trash2, Upload } from "lucide-react";
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
  | { type: "default"; profile: ConfigProfileSummary }
  | { type: "restore" };

type CreateMode = "fields" | "raw";

const columns = (
  selectProfile: (profile: ConfigProfileSummary) => void,
  editProfile: (profile: ConfigProfileSummary) => void,
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
        <Button size="sm" variant="outline" onClick={() => editProfile(profile)}><Pencil className="h-3.5 w-3.5" />Editar</Button>
        <Button size="sm" variant="outline" onClick={() => setPending({ type: "apply", profile })}><ShieldCheck className="h-3.5 w-3.5" />Aplicar</Button>
        {!profile.isDefault ? <Button size="sm" variant="outline" onClick={() => setPending({ type: "default", profile })}><Star className="h-3.5 w-3.5" />Default</Button> : null}
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
  const [editing, setEditing] = React.useState(false);
  const [editDetail, setEditDetail] = React.useState<ConfigProfileDetail | null>(null);
  const [editDiff, setEditDiff] = React.useState<ConfigProfileDiffEntry[]>([]);
  const [pending, setPending] = React.useState<PendingAction | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [createMode, setCreateMode] = React.useState<CreateMode>("fields");
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
    setEditing(false);
    setEditDetail(null);
    setEditDiff([]);
    setSelectedProfileId(null);
    loadProfiles(null).catch((error) => toast({ title: "No se pudieron cargar perfiles", description: error.message, variant: "error" }));
  }, [loadProfiles, selectedServerId, toast]);

  const selectProfile = async (profile: ConfigProfileSummary) => {
    if (!selectedServerId) return;
    setSelectedProfileId(profile.id);
    setDetail(await api.configProfile(selectedServerId, profile.id));
    setDiff(await api.configProfileDiff(selectedServerId, profile.id));
  };

  const editProfile = async (profile: ConfigProfileSummary) => {
    if (!selectedServerId) return;
    setBusy(true);
    try {
      const loaded = await api.configProfile(selectedServerId, profile.id);
      const nextDiff = await api.configProfileDiff(selectedServerId, profile.id);
      setSelectedProfileId(profile.id);
      setDetail(loaded);
      setDiff(nextDiff);
      setEditDetail(loaded);
      setEditDiff(nextDiff);
      setEditing(true);
    } catch (error) {
      toast({ title: "No se pudo abrir el perfil", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const closeEditor = () => {
    setEditing(false);
    setEditDetail(null);
    setEditDiff([]);
  };

  const createProfile = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedServerId) return;
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setBusy(true);
    try {
      const name = String(form.get("name") || "");
      const description = String(form.get("description") || "");
      const payload: {
        name: string;
        description: string;
        configuration?: string;
        baseProfileId?: string;
        values?: Record<string, string>;
      } = { name, description };

      if (createMode === "raw") {
        payload.configuration = String(form.get("createConfiguration") || "");
      } else {
        if (!detail) {
          throw new Error("Selecciona un perfil base para crear usando campos.");
        }
        const values: Record<string, string> = {};
        detail.fields.forEach((field) => {
          values[field.name] = String(form.get(`create:${field.name}`) ?? field.value);
        });
        payload.baseProfileId = detail.id;
        payload.values = values;
      }

      const created = await api.createConfigProfile(selectedServerId, {
        ...payload
      });
      formElement.reset();
      setSelectedProfileId(created.id);
      setDetail(created);
      toast({ title: "Perfil creado", description: "Se guardo con la configuracion indicada." });
      await loadProfiles(created.id);
    } catch (error) {
      toast({ title: "No se pudo crear", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const saveDetail = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedServerId || !editDetail) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const saved = await api.updateConfigProfile(selectedServerId, editDetail.id, {
        name: String(form.get("name") || editDetail.name),
        description: String(form.get("description") || ""),
        configuration: String(form.get("configuration") ?? editDetail.configuration)
      });
      setSelectedProfileId(saved.id);
      setDetail(saved);
      setEditDetail(saved);
      toast({ title: "Perfil guardado", description: "Los cambios quedaron persistidos." });
      try {
        const nextDiff = await api.configProfileDiff(selectedServerId, saved.id);
        setDiff(nextDiff);
        setEditDiff(nextDiff);
        await loadProfiles(saved.id);
      } catch (refreshError) {
        toast({ title: "Perfil guardado", description: "Se guardo correctamente, pero no se pudo refrescar la vista completa.", variant: "error" });
      }
    } catch (error) {
      toast({ title: "No se pudo guardar", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const saveEditParameters = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedServerId || !editDetail) return;
    const form = new FormData(event.currentTarget);
    const values: Record<string, string> = {};
    editDetail.fields.forEach((field) => {
      values[field.name] = String(form.get(`editParam:${field.name}`) ?? field.value);
    });
    setBusy(true);
    try {
      const saved = await api.updateConfigProfileParameters(selectedServerId, editDetail.id, values);
      setSelectedProfileId(saved.id);
      setDetail(saved);
      setEditDetail(saved);
      toast({ title: "Parametros guardados", description: "El perfil se actualizo sobre el mismo registro." });
      try {
        const nextDiff = await api.configProfileDiff(selectedServerId, saved.id);
        setDiff(nextDiff);
        setEditDiff(nextDiff);
        await loadProfiles(saved.id);
      } catch (refreshError) {
        toast({ title: "Parametros guardados", description: "Se guardaron correctamente, pero no se pudo refrescar la vista completa.", variant: "error" });
      }
    } catch (error) {
      toast({ title: "No se pudieron guardar parametros", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const saveParameters = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedServerId || !detail) return;
    const form = new FormData(event.currentTarget);
    const values: Record<string, string> = {};
    detail.fields.forEach((field) => {
      values[field.name] = String(form.get(`param:${field.name}`) ?? field.value);
    });
    setBusy(true);
    try {
      const saved = await api.updateConfigProfileParameters(selectedServerId, detail.id, values);
      setSelectedProfileId(saved.id);
      setDetail(saved);
      setDiff(await api.configProfileDiff(selectedServerId, saved.id));
      toast({ title: "Parametros guardados", description: "El perfil se actualizo sin tocar campos no listados." });
      await loadProfiles(saved.id);
    } catch (error) {
      toast({ title: "No se pudieron guardar parametros", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const selectedSummary = list?.profiles.find((profile) => profile.id === detail?.id) ?? null;

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
      } else if (pending.type === "default") {
        const updated = await api.markConfigProfileDefault(selectedServerId, pending.profile.id);
        setSelectedProfileId(updated.id);
        setDetail(updated);
        toast({ title: "Default actualizado", description: `${updated.name} queda protegido como default.` });
      } else if (pending.type === "restore") {
        const result = await api.restoreDefaultConfigProfile(selectedServerId);
        toast({ title: "Default restaurado", description: result.message });
      } else {
        const result = await api.applyConfigProfile(selectedServerId, pending.profile.id);
        toast({ title: "Perfil aplicado", description: result.message });
      }
      setPending(null);
      await loadProfiles(pending.type === "delete" ? null : pending.type === "restore" ? list?.defaultProfileId ?? null : pending.profile.id);
    } catch (error) {
      toast({ title: "La operacion fallo", description: error instanceof Error ? error.message : "Error desconocido", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  const pendingDescription = pending?.type === "delete"
    ? `Se eliminara ${pending.profile.name}. Esta accion no aplica al perfil marcado como default ni al activo.`
    : pending?.type === "default"
      ? `${pending.profile.name} quedara marcado como default. Desde ese momento no podra eliminarse ni renombrarse hasta marcar otro perfil.`
    : pending?.type === "restore"
      ? "Se aplicara el perfil default. Se creara un respaldo antes de escribir el archivo activo."
      : pending
        ? `Se aplicara ${pending.profile.name}. Se creara un respaldo y Palworld debera reiniciarse despues.`
        : "";

  if (editing) {
    return (
      <div>
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <SectionHeader title="Editar perfil" description="Modifica el perfil seleccionado y guarda los cambios sobre el mismo registro." />
          <Button variant="outline" onClick={closeEditor}><ArrowLeft className="h-4 w-4" />Volver</Button>
        </div>

        {!editDetail ? (
          <Card>
            <CardContent className="py-8">
              <EmptyState title="Cargando perfil" description="Espera un momento mientras se carga la configuracion." />
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>{editDetail.name}</CardTitle>
                <CardDescription>Esta pantalla guarda directamente el perfil {editDetail.id}.</CardDescription>
              </CardHeader>
              <CardContent>
                <form key={`edit-${editDetail.id}-${editDetail.updatedAt}`} className="space-y-4" onSubmit={saveDetail}>
                  <div className="grid gap-3 md:grid-cols-[1fr_1fr_auto]">
                    <label className="text-sm">
                      Nombre
                      <input name="name" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3 read-only:bg-muted read-only:text-muted-foreground" defaultValue={editDetail.name} readOnly={editDetail.isDefault} required />
                    </label>
                    <label className="text-sm">
                      Descripcion
                      <input name="description" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" defaultValue={editDetail.description} />
                    </label>
                    <label className="text-sm">
                      Parametros
                      <input className="mt-1 h-10 w-full rounded-md border border-input bg-muted px-3 text-muted-foreground" value={editDetail.parameterCount} readOnly />
                    </label>
                  </div>
                  {editDetail.isDefault ? (
                    <div className="rounded-md border border-cyan-400/30 bg-cyan-400/10 p-3 text-sm text-cyan-100">
                      Este perfil esta marcado como default: no se puede renombrar ni eliminar. Marca otro perfil como default para liberar este.
                    </div>
                  ) : null}
                  <label className="block text-sm">
                    Configuracion completa
                    <textarea name="configuration" className="focus-ring mt-1 h-[36rem] w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs" defaultValue={editDetail.configuration} spellCheck={false} />
                  </label>
                  <div className="flex justify-end">
                    <Button type="submit" disabled={busy}><Save className="h-4 w-4" />Guardar perfil</Button>
                  </div>
                </form>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Parametros editables</CardTitle>
                <CardDescription>Guarda estos campos sobre el mismo perfil sin crear una copia.</CardDescription>
              </CardHeader>
              <CardContent>
                {editDetail.fields.length === 0 ? (
                  <EmptyState title="Sin parametros" description="El perfil no contiene OptionSettings legible." />
                ) : (
                  <form key={`edit-fields-${editDetail.id}-${editDetail.updatedAt}`} className="space-y-4" onSubmit={saveEditParameters}>
                    <div className="grid max-h-[70vh] gap-3 overflow-auto pr-1 md:grid-cols-2 xl:grid-cols-3">
                      {editDetail.fields.map((field) => (
                        <label key={field.name} className="text-sm">
                          {field.name}
                          <input name={`editParam:${field.name}`} className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3 font-mono text-xs" defaultValue={field.value} />
                        </label>
                      ))}
                    </div>
                    <div className="flex justify-end">
                      <Button type="submit" disabled={busy}><Save className="h-4 w-4" />Guardar parametros</Button>
                    </div>
                  </form>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Resumen de cambios</CardTitle>
                <CardDescription>Diferencias entre el INI activo y este perfil.</CardDescription>
              </CardHeader>
              <CardContent>
                {editDiff.length === 0 ? (
                  <EmptyState title="Sin diferencias" description="El perfil coincide con la configuracion activa o aun no hay comparacion." />
                ) : (
                  <div className="max-h-72 overflow-auto rounded-md border border-border">
                    <table className="w-full min-w-[520px] text-sm">
                      <thead className="sticky top-0 bg-card text-left text-muted-foreground">
                        <tr><th className="p-3">Campo</th><th className="p-3">Actual</th><th className="p-3">Perfil</th></tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {editDiff.slice(0, 100).map((item) => (
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
        )}
      </div>
    );
  }

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
              <CardTitle>Crear perfil</CardTitle>
              <CardDescription>Nombre, descripcion y configuracion del nuevo perfil en un solo lugar.</CardDescription>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={createProfile}>
                <div className="grid gap-3 md:grid-cols-[1fr_1.4fr]">
                  <label className="text-sm">
                    Nombre
                    <input name="name" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" placeholder="Ej. PVP fin de semana" required />
                  </label>
                  <label className="text-sm">
                    Descripcion
                    <input name="description" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" placeholder="Notas del perfil" />
                  </label>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium">Configuracion</p>
                    <p className="text-xs text-muted-foreground">
                      {detail ? `Base: ${detail.name}` : "Selecciona un perfil para cargar campos editables."}
                    </p>
                  </div>
                  <div className="inline-flex rounded-md border border-border bg-muted p-1">
                    <button
                      type="button"
                      className={`rounded px-3 py-1.5 text-sm ${createMode === "fields" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground"}`}
                      onClick={() => setCreateMode("fields")}
                    >
                      Campos
                    </button>
                    <button
                      type="button"
                      className={`rounded px-3 py-1.5 text-sm ${createMode === "raw" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:text-foreground"}`}
                      onClick={() => setCreateMode("raw")}
                    >
                      Texto plano
                    </button>
                  </div>
                </div>

                {createMode === "fields" ? (
                  !detail ? (
                    <EmptyState title="Sin perfil base" description="Selecciona un perfil de la tabla para usarlo como base." />
                  ) : detail.fields.length === 0 ? (
                    <EmptyState title="Sin parametros" description="El perfil base no contiene OptionSettings legible." />
                  ) : (
                    <div key={`create-fields-${detail.id}`} className="grid max-h-[50vh] gap-3 overflow-auto rounded-md border border-border p-3 md:grid-cols-2">
                      {detail.fields.map((field) => (
                        <label key={field.name} className="text-sm">
                          {field.name}
                          <input name={`create:${field.name}`} className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3 font-mono text-xs" defaultValue={field.value} />
                        </label>
                      ))}
                    </div>
                  )
                ) : (
                  <label className="block text-sm">
                    Configuracion completa
                    <textarea
                      key={`create-raw-${detail?.id ?? "empty"}`}
                      name="createConfiguration"
                      className="focus-ring mt-1 h-72 w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
                      defaultValue={detail?.configuration ?? ""}
                      placeholder="Pega aqui el contenido completo de PalWorldSettings.ini"
                      spellCheck={false}
                    />
                  </label>
                )}

                <div className="flex justify-end">
                  <Button type="submit" disabled={busy || !selectedServerId || (createMode === "fields" && (!detail || detail.fields.length === 0))}>
                    <FileJson className="h-4 w-4" />Crear perfil
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

        </div>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Perfil seleccionado</CardTitle>
              <CardDescription>Vista previa del perfil. Usa Editar para modificarlo en su propia pantalla.</CardDescription>
            </CardHeader>
            <CardContent>
              {!detail ? (
                <EmptyState title="Selecciona un perfil" description="Elige un registro de la tabla para ver su configuracion." />
              ) : (
                <div className="space-y-4">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="text-sm text-muted-foreground">
                      Nombre
                      <input className="mt-1 h-10 w-full rounded-md border border-input bg-muted px-3 text-foreground" value={detail.name} readOnly />
                    </label>
                    <label className="text-sm text-muted-foreground">
                      Parametros
                      <input className="mt-1 h-10 w-full rounded-md border border-input bg-muted px-3 text-muted-foreground" value={detail.parameterCount} readOnly />
                    </label>
                  </div>
                  <label className="block text-sm text-muted-foreground">
                    Descripcion
                    <textarea className="mt-1 h-20 w-full rounded-md border border-input bg-muted px-3 py-2 text-foreground" value={detail.description} readOnly />
                  </label>
                  <label className="block text-sm text-muted-foreground">
                    Configuracion del perfil
                    <textarea className="mt-1 h-72 w-full rounded-md border border-input bg-muted px-3 py-2 font-mono text-xs text-foreground" value={detail.configuration} readOnly spellCheck={false} />
                  </label>
                  <div className="flex flex-wrap justify-between gap-2">
                    <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-md border border-border px-4 text-sm hover:bg-accent">
                      <Upload className="h-4 w-4" />
                      Importar JSON
                      <input type="file" accept="application/json,.json" className="hidden" onChange={importProfile} />
                    </label>
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" variant="outline" disabled={busy || !list?.defaultProfileId} onClick={() => setPending({ type: "restore" })}><RotateCcw className="h-4 w-4" />Restaurar default</Button>
                      {selectedSummary ? <Button type="button" onClick={() => editProfile(selectedSummary)} disabled={busy}><Pencil className="h-4 w-4" />Editar perfil</Button> : null}
                    </div>
                  </div>
                </div>
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

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Perfiles disponibles</CardTitle>
          <CardDescription>El perfil marcado como default queda protegido. Puedes crear nuevos perfiles arriba o duplicar uno existente.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {!list ? (
            <div className="space-y-3"><Skeleton className="h-12" /><Skeleton className="h-12" /></div>
          ) : list.profiles.length === 0 ? (
            <EmptyState title="Sin perfiles" description="Al cargar el servidor se debe crear default automaticamente." />
          ) : (
            <DataTable
              data={list.profiles}
              columns={columns(selectProfile, editProfile, setPending, duplicateProfile, exportProfile)}
              getRowKey={(profile) => profile.id}
              searchPlaceholder="Filtrar perfiles"
              minWidth="980px"
            />
          )}
        </CardContent>
      </Card>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Parametros del perfil</CardTitle>
          <CardDescription>Selecciona un perfil de la tabla y edita sus valores cargados desde OptionSettings.</CardDescription>
        </CardHeader>
        <CardContent>
          {!detail ? (
            <EmptyState title="Sin perfil seleccionado" description="Selecciona un perfil para cargar sus parametros." />
          ) : detail.fields.length === 0 ? (
            <EmptyState title="Sin parametros" description="El perfil no contiene OptionSettings legible." />
          ) : (
            <div key={`${detail.id}-fields`} className="space-y-4">
              <div className="grid max-h-[70vh] gap-3 overflow-auto pr-1 md:grid-cols-2 xl:grid-cols-3">
                {detail.fields.map((field) => (
                  <label key={field.name} className="text-sm text-muted-foreground">
                    {field.name}
                    <input className="mt-1 h-10 w-full rounded-md border border-input bg-muted px-3 font-mono text-xs text-foreground" value={field.value} readOnly />
                  </label>
                ))}
              </div>
              <div className="flex justify-end">
                {selectedSummary ? <Button type="button" onClick={() => editProfile(selectedSummary)} disabled={busy}><Pencil className="h-4 w-4" />Editar parametros</Button> : null}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={Boolean(pending)}
        busy={busy}
        title="Confirmar perfiles"
        description={pendingDescription}
        confirmLabel={pending?.type === "delete" ? "Eliminar" : pending?.type === "default" ? "Marcar default" : "Aplicar"}
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
