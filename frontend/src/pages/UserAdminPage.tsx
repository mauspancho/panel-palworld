import * as React from "react";
import { Eye, EyeOff, RefreshCw, RotateCcw, Save, Search, UserPlus, Unlock } from "lucide-react";
import { api, ApiError } from "../lib/api";
import type { UserRole, UserView } from "../types";
import { DataTable, type DataTableColumn } from "../components/DataTable";
import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { ConfirmDialog } from "../components/ui/confirm-dialog";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";
import { formatDateTime } from "../lib/utils";

const emptyForm = {
  displayName: "",
  username: "",
  email: "",
  password: "",
  confirmPassword: "",
  role: "USER" as UserRole,
  enabled: true,
  mustChangePassword: true
};

export function UserAdminPage() {
  const [users, setUsers] = React.useState<UserView[] | null>(null);
  const [selected, setSelected] = React.useState<UserView | null>(null);
  const [form, setForm] = React.useState(emptyForm);
  const [editing, setEditing] = React.useState({ displayName: "", email: "", role: "USER" as UserRole, enabled: true, mustChangePassword: false });
  const [reset, setReset] = React.useState({ password: "", confirmPassword: "", mustChangePassword: true });
  const [search, setSearch] = React.useState("");
  const [role, setRole] = React.useState<UserRole | "">("");
  const [enabled, setEnabled] = React.useState("");
  const [busy, setBusy] = React.useState("");
  const [showCreatePasswords, setShowCreatePasswords] = React.useState(false);
  const [showResetPasswords, setShowResetPasswords] = React.useState(false);
  const [confirmDisable, setConfirmDisable] = React.useState(false);
  const { toast } = useToast();

  const load = React.useCallback(async () => {
    setUsers(null);
    setUsers(await api.users({ search, role, enabled }));
  }, [search, role, enabled]);

  React.useEffect(() => {
    load().catch((error) => toast({ title: "No se pudieron cargar usuarios", description: message(error), variant: "error" }));
  }, [load, toast]);

  const select = (user: UserView) => {
    setSelected(user);
    setEditing({
      displayName: user.displayName || "",
      email: user.email || "",
      role: user.role,
      enabled: user.enabled,
      mustChangePassword: user.mustChangePassword
    });
    setReset({ password: "", confirmPassword: "", mustChangePassword: true });
  };

  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy("create");
    try {
      const created = await api.createUser(form);
      setForm(emptyForm);
      await load();
      select(created);
      toast({ title: "Usuario creado", description: `${created.username} ya esta disponible.` });
    } catch (error) {
      toast({ title: "No se pudo crear", description: message(error), variant: "error" });
    } finally {
      setBusy("");
    }
  };

  const save = async () => {
    if (!selected) return;
    setBusy("save");
    try {
      const updated = await api.updateUser(selected.id, editing);
      await load();
      select(updated);
      toast({ title: "Usuario actualizado", description: "Los cambios quedaron guardados." });
    } catch (error) {
      toast({ title: "No se pudo actualizar", description: message(error), variant: "error" });
    } finally {
      setBusy("");
      setConfirmDisable(false);
    }
  };

  const resetPassword = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selected) return;
    setBusy("reset");
    try {
      await api.resetUserPassword(selected.id, reset);
      setReset({ password: "", confirmPassword: "", mustChangePassword: true });
      await load();
      toast({ title: "Contrasena restablecida", description: "La cuenta fue desbloqueada y los intentos fallidos se reiniciaron." });
    } catch (error) {
      toast({ title: "No se pudo restablecer", description: message(error), variant: "error" });
    } finally {
      setBusy("");
    }
  };

  const unlock = async () => {
    if (!selected) return;
    setBusy("unlock");
    try {
      const updated = await api.unlockUser(selected.id);
      await load();
      select(updated);
      toast({ title: "Cuenta desbloqueada", description: updated.username });
    } catch (error) {
      toast({ title: "No se pudo desbloquear", description: message(error), variant: "error" });
    } finally {
      setBusy("");
    }
  };

  const submitEdit = (event: React.FormEvent) => {
    event.preventDefault();
    if (selected?.enabled && !editing.enabled) {
      setConfirmDisable(true);
      return;
    }
    save();
  };

  const userColumns: DataTableColumn<UserView>[] = [
    {
      key: "displayName",
      header: "Nombre",
      sortable: true,
      searchValue: (user) => `${user.displayName} ${user.email || ""}`,
      render: (user) => (
        <div>
          <div className="font-medium">{user.displayName}</div>
          <div className="text-xs text-muted-foreground">{user.email || "Sin correo"}</div>
        </div>
      )
    },
    { key: "username", header: "Usuario", sortable: true, searchValue: (user) => user.username, render: (user) => user.username },
    { key: "role", header: "Rol", sortable: true, searchValue: (user) => user.role, render: (user) => <Badge>{user.role}</Badge> },
    {
      key: "status",
      header: "Estado",
      sortable: true,
      searchValue: (user) => `${user.enabled ? "Activo" : "Inactivo"} ${user.locked ? "Bloqueado" : ""} ${user.mustChangePassword ? "Cambio requerido" : ""}`,
      sortValue: (user) => `${user.enabled ? "1" : "0"}-${user.locked ? "1" : "0"}-${user.mustChangePassword ? "1" : "0"}`,
      render: (user) => (
        <div className="flex flex-wrap gap-1">
          <Badge variant={user.enabled ? "success" : "muted"}>{user.enabled ? "Activo" : "Inactivo"}</Badge>
          {user.locked ? <Badge variant="danger">Bloqueado</Badge> : null}
          {user.mustChangePassword ? <Badge variant="warning">Cambio requerido</Badge> : null}
        </div>
      )
    },
    { key: "lastLoginAt", header: "Ultimo acceso", sortable: true, searchValue: (user) => formatDateTime(user.lastLoginAt), sortValue: (user) => user.lastLoginAt || "", render: (user) => formatDateTime(user.lastLoginAt) }
  ];

  return (
    <div>
      <SectionHeader title="Administracion de usuarios" description="Crea usuarios, cambia roles, desbloquea cuentas y restablece contrasenas." />
      <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <CardTitle>Usuarios</CardTitle>
                <CardDescription>Los nombres de usuario se comparan sin mayusculas ni espacios externos.</CardDescription>
              </div>
              <Button variant="outline" size="sm" onClick={load}>
                <RefreshCw className="h-4 w-4" />
                Actualizar
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="mb-4 grid gap-3 lg:grid-cols-[1fr_150px_150px]">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                <input className="focus-ring h-9 w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar nombre o usuario" />
              </div>
              <select className="focus-ring h-9 rounded-md border border-input bg-background px-2 text-sm" value={role} onChange={(event) => setRole(event.target.value as UserRole | "")}>
                <option value="">Todos los roles</option>
                <option value="ADMIN">ADMIN</option>
                <option value="USER">USER</option>
              </select>
              <select className="focus-ring h-9 rounded-md border border-input bg-background px-2 text-sm" value={enabled} onChange={(event) => setEnabled(event.target.value)}>
                <option value="">Todos</option>
                <option value="true">Activos</option>
                <option value="false">Inactivos</option>
              </select>
            </div>
            {!users ? <Skeleton className="h-72" /> : users.length === 0 ? <EmptyState title="Sin usuarios" description="Ajusta la busqueda o crea un usuario nuevo." /> : (
              <DataTable
                data={users}
                columns={userColumns}
                getRowKey={(user) => user.id}
                searchPlaceholder="Filtrar usuarios cargados"
                onRowClick={select}
              />
            )}
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Crear usuario</CardTitle>
              <CardDescription>El password no se mostrara ni sera recuperable despues de guardar.</CardDescription>
            </CardHeader>
            <CardContent>
              <form className="space-y-3" onSubmit={create}>
                <Input label="Nombre visible" value={form.displayName} onChange={(value) => setForm({ ...form, displayName: value })} required />
                <Input label="Usuario" value={form.username} onChange={(value) => setForm({ ...form, username: value })} required />
                <Input label="Correo opcional" type="email" value={form.email} onChange={(value) => setForm({ ...form, email: value })} />
                <div className="grid gap-3 sm:grid-cols-2">
                  <Password label="Contrasena" value={form.password} visible={showCreatePasswords} onChange={(value) => setForm({ ...form, password: value })} />
                  <Password label="Confirmacion" value={form.confirmPassword} visible={showCreatePasswords} onChange={(value) => setForm({ ...form, confirmPassword: value })} />
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <SelectRole value={form.role} onChange={(value) => setForm({ ...form, role: value })} />
                  <label className="flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
                    <input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} />
                    Activo
                  </label>
                </div>
                <label className="flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
                  <input type="checkbox" checked={form.mustChangePassword} onChange={(event) => setForm({ ...form, mustChangePassword: event.target.checked })} />
                  Obligar cambio en primer inicio
                </label>
                <div className="flex justify-between gap-2">
                  <Button type="button" variant="outline" onClick={() => setShowCreatePasswords((value) => !value)}>
                    {showCreatePasswords ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    {showCreatePasswords ? "Ocultar" : "Mostrar"}
                  </Button>
                  <Button disabled={busy === "create"}>
                    <UserPlus className="h-4 w-4" />
                    {busy === "create" ? "Creando..." : "Crear"}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Usuario seleccionado</CardTitle>
              <CardDescription>{selected ? `${selected.username} creado por ${selected.createdBy || "-"}` : "Selecciona un usuario de la lista."}</CardDescription>
            </CardHeader>
            <CardContent>
              {!selected ? <EmptyState title="Sin seleccion" description="Elige un usuario para editarlo." /> : (
                <div className="space-y-5">
                  <form className="space-y-3" onSubmit={submitEdit}>
                    <Input label="Nombre visible" value={editing.displayName} onChange={(value) => setEditing({ ...editing, displayName: value })} required />
                    <Input label="Correo opcional" type="email" value={editing.email} onChange={(value) => setEditing({ ...editing, email: value })} />
                    <div className="grid gap-3 sm:grid-cols-2">
                      <SelectRole value={editing.role} onChange={(value) => setEditing({ ...editing, role: value })} />
                      <label className="flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
                        <input type="checkbox" checked={editing.enabled} onChange={(event) => setEditing({ ...editing, enabled: event.target.checked })} />
                        Activo
                      </label>
                    </div>
                    <label className="flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
                      <input type="checkbox" checked={editing.mustChangePassword} onChange={(event) => setEditing({ ...editing, mustChangePassword: event.target.checked })} />
                      Cambio obligatorio en siguiente inicio
                    </label>
                    <div className="flex flex-wrap justify-between gap-2">
                      <Button type="button" variant="outline" onClick={unlock} disabled={!selected.locked || busy === "unlock"}>
                        <Unlock className="h-4 w-4" />
                        Desbloquear
                      </Button>
                      <Button disabled={busy === "save"}>
                        <Save className="h-4 w-4" />
                        {busy === "save" ? "Guardando..." : "Guardar cambios"}
                      </Button>
                    </div>
                  </form>
                  <form className="space-y-3 border-t border-border pt-4" onSubmit={resetPassword}>
                    <div className="font-medium">Restablecer contrasena</div>
                    <div className="grid gap-3 sm:grid-cols-2">
                      <Password label="Temporal" value={reset.password} visible={showResetPasswords} onChange={(value) => setReset({ ...reset, password: value })} />
                      <Password label="Confirmacion" value={reset.confirmPassword} visible={showResetPasswords} onChange={(value) => setReset({ ...reset, confirmPassword: value })} />
                    </div>
                    <label className="flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm">
                      <input type="checkbox" checked={reset.mustChangePassword} onChange={(event) => setReset({ ...reset, mustChangePassword: event.target.checked })} />
                      Obligar cambio al iniciar
                    </label>
                    <div className="flex justify-between gap-2">
                      <Button type="button" variant="outline" onClick={() => setShowResetPasswords((value) => !value)}>
                        {showResetPasswords ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                        {showResetPasswords ? "Ocultar" : "Mostrar"}
                      </Button>
                      <Button variant="secondary" disabled={busy === "reset"}>
                        <RotateCcw className="h-4 w-4" />
                        {busy === "reset" ? "Restableciendo..." : "Restablecer"}
                      </Button>
                    </div>
                  </form>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
      <ConfirmDialog
        open={confirmDisable}
        title="Desactivar cuenta"
        description="La cuenta no podra iniciar sesion ni ejecutar acciones. El historial se conserva."
        confirmLabel="Desactivar"
        busy={busy === "save"}
        onCancel={() => setConfirmDisable(false)}
        onConfirm={save}
      />
    </div>
  );
}

function Input({ label, value, onChange, type = "text", required }: { label: string; value: string; onChange: (value: string) => void; type?: string; required?: boolean }) {
  return (
    <div>
      <label className="text-sm text-muted-foreground">{label}</label>
      <input className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" type={type} value={value} onChange={(event) => onChange(event.target.value)} required={required} />
    </div>
  );
}

function Password({ label, value, onChange, visible }: { label: string; value: string; onChange: (value: string) => void; visible: boolean }) {
  return <Input label={label} value={value} onChange={onChange} type={visible ? "text" : "password"} required />;
}

function SelectRole({ value, onChange }: { value: UserRole; onChange: (value: UserRole) => void }) {
  return (
    <div>
      <label className="text-sm text-muted-foreground">Rol</label>
      <select className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-2" value={value} onChange={(event) => onChange(event.target.value as UserRole)}>
        <option value="USER">USER</option>
        <option value="ADMIN">ADMIN</option>
      </select>
    </div>
  );
}

function message(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message;
  }
  return "Operacion no completada.";
}
