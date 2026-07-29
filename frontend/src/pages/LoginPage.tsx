import * as React from "react";
import { Lock, Shield } from "lucide-react";
import { api } from "../lib/api";
import type { UserSession } from "../types";
import { applyTheme, type ThemeMode } from "../lib/theme";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { ThemeToggle } from "../components/ThemeToggle";
import { useToast } from "../components/ui/toast";

export function LoginPage({ onLogin }: { onLogin: (session: UserSession) => void }) {
  const [username, setUsername] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [theme, setTheme] = React.useState<ThemeMode>("dark");
  const { toast } = useToast();

  React.useEffect(() => {
    applyTheme(theme);

    if (theme !== "system") {
      return;
    }

    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const listener = () => applyTheme("system");
    media.addEventListener("change", listener);
    return () => media.removeEventListener("change", listener);
  }, [theme]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const session = await api.login(username, password);
      onLogin(session);
    } catch (error) {
      toast({ title: "No se pudo iniciar sesion", description: error instanceof Error ? error.message : "Revisa usuario y password.", variant: "error" });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center p-4">
      <div className="absolute right-4 top-4">
        <ThemeToggle value={theme} onChange={setTheme} />
      </div>
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-md bg-cyan-400/15 text-cyan-200 ring-1 ring-cyan-300/25">
            <Shield className="h-6 w-6" />
          </div>
          <CardTitle className="text-2xl">Palworld Admin</CardTitle>
          <CardDescription>Acceso seguro al panel de servidores.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div>
              <label className="text-sm text-muted-foreground" htmlFor="username">Usuario</label>
              <input
                id="username"
                className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground" htmlFor="password">Password</label>
              <input
                id="password"
                className="focus-ring mt-1 h-10 w-full rounded-md border border-input bg-background px-3"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
            <Button className="w-full" disabled={busy}>
              <Lock className="h-4 w-4" />
              {busy ? "Validando..." : "Entrar"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
