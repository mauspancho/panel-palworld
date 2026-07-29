import * as React from "react";
import { Eye, EyeOff, Save } from "lucide-react";
import { api, ApiError } from "../lib/api";
import type { UserSession, UserView } from "../types";
import { SectionHeader } from "../components/SectionHeader";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Skeleton } from "../components/ui/skeleton";
import { useToast } from "../components/ui/toast";
import { formatDateTime } from "../lib/utils";

type Props = {
  forced?: boolean;
  onSessionUpdate: (session: UserSession) => void;
};

export function ProfilePage({ forced, onSessionUpdate }: Props) {
  const [profile, setProfile] = React.useState<UserView | null>(null);
  const [displayName, setDisplayName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const [currentPassword, setCurrentPassword] = React.useState("");
  const [newPassword, setNewPassword] = React.useState("");
  const [confirmPassword, setConfirmPassword] = React.useState("");
  const [showPasswords, setShowPasswords] = React.useState(false);
  const [savingProfile, setSavingProfile] = React.useState(false);
  const [savingPassword, setSavingPassword] = React.useState(false);
  const { toast } = useToast();

  const load = React.useCallback(async () => {
    const next = await api.profile();
    setProfile(next);
    setDisplayName(next.displayName || "");
    setEmail(next.email || "");
  }, []);

  React.useEffect(() => {
    load().catch((error) => toast({ title: "No se pudo cargar el perfil", description: error.message, variant: "error" }));
  }, [load, toast]);

  const saveProfile = async (event: React.FormEvent) => {
    event.preventDefault();
    setSavingProfile(true);
    try {
      const updated = await api.updateProfile({ displayName, email });
      setProfile(updated);
      const session = await api.me();
      onSessionUpdate(session);
      toast({ title: "Perfil actualizado", description: "Los cambios ya se muestran en el panel." });
    } catch (error) {
      toast({ title: "No se pudo guardar", description: message(error), variant: "error" });
    } finally {
      setSavingProfile(false);
    }
  };

  const savePassword = async (event: React.FormEvent) => {
    event.preventDefault();
    setSavingPassword(true);
    try {
      await api.changePassword({ currentPassword, newPassword, confirmPassword });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      const session = await api.me();
      onSessionUpdate(session);
      await load();
      toast({ title: "Contrasena actualizada", description: "Ya puedes continuar usando el panel." });
    } catch (error) {
      toast({ title: "No se pudo cambiar la contrasena", description: message(error), variant: "error" });
    } finally {
      setSavingPassword(false);
    }
  };

  return (
    <div>
      <SectionHeader
        title="Mi perfil"
        description={forced ? "Debes cambiar tu contrasena antes de continuar." : "Informacion personal y seguridad de tu cuenta."}
      />
      {forced ? (
        <div className="mb-4 rounded-md border border-amber-400/35 bg-amber-400/10 p-3 text-sm text-amber-100">
          Tu cuenta tiene marcado cambio obligatorio de contrasena. Las funciones principales se habilitan despues de actualizarla.
        </div>
      ) : null}
      {!profile ? (
        <Skeleton className="h-96" />
      ) : (
        <div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
          <Card>
            <CardHeader>
              <CardTitle>Informacion personal</CardTitle>
              <CardDescription>Tu usuario y rol no se pueden modificar desde el perfil.</CardDescription>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={saveProfile}>
                <div className="grid gap-4 sm:grid-cols-2">
                  <ReadOnly label="Usuario" value={profile.username} />
                  <ReadOnly label="Rol" value={profile.role === "ADMIN" ? "Administrador" : "Usuario"} />
                  <ReadOnly label="Estado" value={profile.enabled ? "Activo" : "Inactivo"} />
                  <ReadOnly label="Creado" value={formatDateTime(profile.createdAt)} />
                  <ReadOnly label="Ultimo acceso" value={formatDateTime(profile.lastLoginAt)} />
                  <ReadOnly label="Ultimo cambio de password" value={formatDateTime(profile.passwordChangedAt)} />
                </div>
                <div>
                  <label className="text-sm text-muted-foreground" htmlFor="displayName">Nombre visible</label>
                  <input id="displayName" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" value={displayName} onChange={(event) => setDisplayName(event.target.value)} required maxLength={120} />
                </div>
                <div>
                  <label className="text-sm text-muted-foreground" htmlFor="email">Correo electronico</label>
                  <input id="email" className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
                </div>
                <Button disabled={savingProfile || forced}>
                  <Save className="h-4 w-4" />
                  {savingProfile ? "Guardando..." : "Guardar perfil"}
                </Button>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <div>
                  <CardTitle>Cambiar contrasena</CardTitle>
                  <CardDescription>Minimo 10 caracteres, una letra y un numero.</CardDescription>
                </div>
                {profile.mustChangePassword ? <Badge variant="warning">Obligatorio</Badge> : null}
              </div>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={savePassword}>
                <PasswordField id="currentPassword" label="Contrasena actual" value={currentPassword} onChange={setCurrentPassword} visible={showPasswords} autoComplete="current-password" />
                <PasswordField id="newPassword" label="Nueva contrasena" value={newPassword} onChange={setNewPassword} visible={showPasswords} autoComplete="new-password" />
                <PasswordField id="confirmPassword" label="Confirmacion" value={confirmPassword} onChange={setConfirmPassword} visible={showPasswords} autoComplete="new-password" />
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <Button type="button" variant="outline" onClick={() => setShowPasswords((value) => !value)}>
                    {showPasswords ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    {showPasswords ? "Ocultar" : "Mostrar"}
                  </Button>
                  <Button disabled={savingPassword}>
                    <Save className="h-4 w-4" />
                    {savingPassword ? "Guardando..." : "Cambiar contrasena"}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}

function ReadOnly({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs uppercase text-muted-foreground">{label}</div>
      <div className="mt-1 min-h-10 rounded-md border border-border bg-muted/30 px-3 py-2 text-sm">{value || "-"}</div>
    </div>
  );
}

function PasswordField({
  id,
  label,
  value,
  onChange,
  visible,
  autoComplete
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  visible: boolean;
  autoComplete: string;
}) {
  return (
    <div>
      <label className="text-sm text-muted-foreground" htmlFor={id}>{label}</label>
      <input id={id} className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3" type={visible ? "text" : "password"} value={value} onChange={(event) => onChange(event.target.value)} autoComplete={autoComplete} required />
    </div>
  );
}

function message(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message;
  }
  return "Operacion no completada.";
}
