import * as React from "react";
import {
  Activity,
  BarChart3,
  ChevronsLeft,
  ChevronsRight,
  Command,
  FileText,
  LogOut,
  Menu,
  FileJson,
  Radio,
  Server,
  Settings,
  Shield,
  UserCog,
  UserCircle,
  Users
} from "lucide-react";
import { ApiError, api } from "./lib/api";
import type { UserSession } from "./types";
import { Button } from "./components/ui/button";
import { ToastProvider, useToast } from "./components/ui/toast";
import { DashboardPage } from "./pages/DashboardPage";
import { ServersPage } from "./pages/ServersPage";
import { PlayersPage } from "./pages/PlayersPage";
import { RconPage } from "./pages/RconPage";
import { LogsPage } from "./pages/LogsPage";
import { ActivityPage } from "./pages/ActivityPage";
import { SettingsPage } from "./pages/SettingsPage";
import { LoginPage } from "./pages/LoginPage";
import { ProfilePage } from "./pages/ProfilePage";
import { UserAdminPage } from "./pages/UserAdminPage";
import { ConfigProfilesPage } from "./pages/ConfigProfilesPage";
import { Skeleton } from "./components/ui/skeleton";
import { cn } from "./lib/utils";
import { ThemeToggle } from "./components/ThemeToggle";
import { useTheme } from "./hooks/useTheme";
import { applyTheme } from "./lib/theme";

export type PageKey = "dashboard" | "servers" | "players" | "rcon" | "commands" | "logs" | "activity" | "configProfiles" | "users" | "profile" | "settings";

const navItems: Array<{ key: PageKey; label: string; icon: React.ComponentType<{ className?: string }>; adminOnly?: boolean }> = [
  { key: "dashboard", label: "Dashboard", icon: BarChart3 },
  { key: "servers", label: "Servidores", icon: Server },
  { key: "players", label: "Jugadores", icon: Users },
  { key: "rcon", label: "Consola RCON", icon: Radio },
  { key: "commands", label: "Comandos", icon: Command },
  { key: "logs", label: "Logs", icon: FileText },
  { key: "activity", label: "Auditoria", icon: Activity },
  { key: "configProfiles", label: "Perfiles", icon: FileJson, adminOnly: true },
  { key: "users", label: "Usuarios", icon: UserCog, adminOnly: true },
  { key: "profile", label: "Mi perfil", icon: UserCircle },
  { key: "settings", label: "Configuracion", icon: Settings }
];

function AppShell() {
  const [session, setSession] = React.useState<UserSession | null>(null);
  const [checking, setChecking] = React.useState(true);
  const [page, setPage] = React.useState<PageKey>("dashboard");
  const [sidebarOpen, setSidebarOpen] = React.useState(true);
  const [selectedServerId, setSelectedServerId] = React.useState<number | null>(null);
  const [selectedLogServerId, setSelectedLogServerId] = React.useState<number | null>(null);
  const [selectedConfigServerId, setSelectedConfigServerId] = React.useState<number | null>(null);
  const { theme, setTheme } = useTheme();
  const { toast } = useToast();

  React.useEffect(() => {
    api
      .me()
      .then(setSession)
      .catch((error) => {
        if (!(error instanceof ApiError && error.status === 401)) {
          toast({ title: "Error de conexion", description: error.message, variant: "error" });
        }
      })
      .finally(() => setChecking(false));
  }, [toast]);

  React.useEffect(() => {
    if (session) {
      applyTheme(theme);
    }
  }, [session, theme]);

  const isAdmin = !!session && (session.roles.includes("ROLE_ADMIN") || session.role === "ADMIN");

  React.useEffect(() => {
    if (session && !isAdmin && (page === "users" || page === "configProfiles")) {
      setPage("dashboard");
    }
  }, [session, isAdmin, page]);

  const logout = async () => {
    await api.logout();
    setSession(null);
  };

  const openRconForServer = (id: number) => {
    setSelectedServerId(id);
    setPage("rcon");
  };

  const openLogsForServer = (id: number) => {
    setSelectedLogServerId(id);
    setPage("logs");
  };

  const openProfilesForServer = (id: number) => {
    setSelectedConfigServerId(id);
    setPage("configProfiles");
  };

  if (checking) {
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <div className="w-full max-w-md space-y-3">
          <Skeleton className="h-10 w-48" />
          <Skeleton className="h-28 w-full" />
        </div>
      </div>
    );
  }

  if (!session) {
    return <LoginPage onLogin={setSession} />;
  }

  const Page = session.mustChangePassword ? <ProfilePage forced onSessionUpdate={setSession} /> : {
    dashboard: <DashboardPage onOpenRcon={openRconForServer} />,
    servers: <ServersPage onOpenRcon={openRconForServer} onOpenLogs={openLogsForServer} onOpenProfiles={openProfilesForServer} isAdmin={isAdmin} />,
    players: <PlayersPage selectedServerId={selectedServerId} onSelectServer={setSelectedServerId} />,
    rcon: <RconPage selectedServerId={selectedServerId} onSelectServer={setSelectedServerId} canManageConfig={isAdmin} />,
    commands: <RconPage selectedServerId={selectedServerId} onSelectServer={setSelectedServerId} commandsOnly />,
    logs: <LogsPage selectedServerId={selectedLogServerId} onSelectServer={setSelectedLogServerId} />,
    activity: <ActivityPage isAdmin={isAdmin} />,
    configProfiles: isAdmin ? <ConfigProfilesPage selectedServerId={selectedConfigServerId} onSelectServer={setSelectedConfigServerId} /> : <DashboardPage onOpenRcon={openRconForServer} />,
    users: isAdmin ? <UserAdminPage /> : <DashboardPage onOpenRcon={openRconForServer} />,
    profile: <ProfilePage onSessionUpdate={setSession} />,
    settings: <SettingsPage />
  }[page];

  return (
    <div className="min-h-screen lg:flex">
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-30 flex w-72 flex-col border-r border-border glass-panel transition-transform lg:static",
          sidebarOpen ? "translate-x-0" : "-translate-x-full lg:w-20 lg:translate-x-0"
        )}
      >
        <div className="flex h-16 items-center justify-between px-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-cyan-400/15 text-cyan-200 ring-1 ring-cyan-300/25">
              <Shield className="h-5 w-5" />
            </div>
            {sidebarOpen ? <div className="font-semibold tracking-normal">Palworld Admin</div> : null}
          </div>
          <Button variant="ghost" size="icon" onClick={() => setSidebarOpen((value) => !value)}>
            {sidebarOpen ? <ChevronsLeft className="h-4 w-4" /> : <ChevronsRight className="h-4 w-4" />}
          </Button>
        </div>
        <nav className="flex-1 space-y-1 px-3 py-3">
          {navItems.filter((item) => !item.adminOnly || isAdmin).map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.key}
                onClick={() => !session.mustChangePassword && setPage(item.key)}
                disabled={session.mustChangePassword && item.key !== "profile"}
                className={cn(
                  "flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left text-sm text-muted-foreground transition hover:bg-accent hover:text-accent-foreground",
                  page === item.key && "bg-primary/10 text-primary ring-1 ring-primary/25"
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                {sidebarOpen ? <span>{item.label}</span> : null}
              </button>
            );
          })}
        </nav>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="sticky top-0 z-20 border-b border-border glass-panel">
          <div className="flex h-16 items-center justify-between px-4 sm:px-6">
            <div className="flex items-center gap-3">
              <Button className="lg:hidden" variant="ghost" size="icon" onClick={() => setSidebarOpen((value) => !value)}>
                <Menu className="h-5 w-5" />
              </Button>
              <div>
                <div className="text-sm text-muted-foreground">Panel administrativo</div>
                <div className="font-semibold">Conexion activa</div>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <ThemeToggle value={theme} onChange={setTheme} />
              <button className="hidden rounded-md px-2 py-1 text-right text-sm hover:bg-accent sm:block" onClick={() => setPage("profile")}>
                <div className="font-medium">{session.displayName || session.username}</div>
                <div className="text-muted-foreground">{session.username} - {session.role === "ADMIN" ? "Administrador" : "Usuario"}</div>
              </button>
              <Button variant="outline" size="sm" onClick={logout}>
                <LogOut className="h-4 w-4" />
                Salir
              </Button>
            </div>
          </div>
        </header>
        <main className="mx-auto max-w-7xl p-4 sm:p-6">{Page}</main>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <ToastProvider>
      <AppShell />
    </ToastProvider>
  );
}
