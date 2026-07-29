import type {
  ActionResult,
  AutoRestartConfig,
  ConfigProfileApplyResult,
  ConfigProfileDetail,
  ConfigProfileDiffEntry,
  ConfigProfileList,
  DashboardView,
  PagedAudit,
  PagedActivity,
  PlayerAverageView,
  PlayerAnalyticsRange,
  PlayerDurationAnalytics,
  PlayerRegistryView,
  RconConfig,
  RconPlayersView,
  RconWelcomeConfig,
  ServerLogsView,
  ServerView,
  UserRole,
  UserSession,
  UserView
} from "../types";

type CsrfToken = {
  token: string;
  headerName: string;
  parameterName: string;
};

const apiBase = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
let csrfToken: CsrfToken | null = null;

function url(path: string) {
  return `${apiBase}${path}`;
}

async function csrf() {
  if (csrfToken) return csrfToken;
  const response = await fetch(url("/api/auth/csrf"), {
    credentials: "include",
    headers: { Accept: "application/json" }
  });
  if (!response.ok) {
    throw new Error("No se pudo obtener CSRF.");
  }
  csrfToken = (await response.json()) as CsrfToken;
  return csrfToken;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");

  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = await csrf();
    headers.set(token.headerName, token.token);
  }

  const response = await fetch(url(path), {
    ...options,
    method,
    headers,
    credentials: "include"
  });

  if (response.status === 401) {
    throw new ApiError("No autenticado.", 401);
  }
  if (!response.ok) {
    const text = await response.text();
    throw new ApiError(text || "La operacion fallo.", response.status);
  }

  const contentType = response.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

export const api = {
  me: () => request<UserSession>("/api/auth/me"),
  login: async (username: string, password: string) => {
    const token = await csrf();
    const body = new URLSearchParams({ username, password });
    body.set(token.parameterName, token.token);
    csrfToken = null;
    const response = await fetch(url("/login"), {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/x-www-form-urlencoded",
        [token.headerName]: token.token
      },
      body,
      redirect: "follow"
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || "El nombre de usuario o la contrasena no son correctos.");
    }
    return request<UserSession>("/api/auth/me");
  },
  logout: async () => {
    const token = await csrf();
    await fetch(url("/logout"), {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json", [token.headerName]: token.token }
    });
    csrfToken = null;
  },
  dashboard: (logPage = 0, logSize = 10) => request<DashboardView>(`/api/dashboard?logPage=${logPage}&logSize=${logSize}`),
  servers: () => request<ServerView[]>("/api/servers"),
  serverLogs: (id: number, lines = 200) => request<ServerLogsView>(`/api/servers/${id}/logs?lines=${lines}`),
  serverAction: (id: number, action: string) => request<ActionResult>(`/api/servers/${id}/actions/${action}`, { method: "POST" }),
  deleteServer: (id: number) => request<ActionResult>(`/api/servers/${id}`, { method: "DELETE" }),
  rconPlayers: (id: number) => request<RconPlayersView>(`/api/servers/${id}/rcon/players`),
  rconBroadcast: (id: number, message: string) =>
    request<{ success: boolean; message: string }>(`/api/servers/${id}/rcon/broadcast`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ message })
    }),
  rconConfig: (id: number) => request<RconConfig>(`/api/servers/${id}/rcon/config`),
  rconWelcomeConfig: (id: number) => request<RconWelcomeConfig>(`/api/servers/${id}/rcon/welcome`),
  autoRestartConfig: (id: number) => request<AutoRestartConfig>(`/api/servers/${id}/auto-restart`),
  playerAverage: (range: PlayerAnalyticsRange = "day") => request<PlayerAverageView>(`/api/player-average?range=${range}`),
  playerAnalytics: (id: number, range: PlayerAnalyticsRange = "week") => request<PlayerDurationAnalytics>(`/api/servers/${id}/player-analytics?range=${range}`),
  playerRegistry: (id: number, range: PlayerAnalyticsRange = "week") => request<PlayerRegistryView>(`/api/servers/${id}/player-registry?range=${range}`),
  saveRconConfig: (id: number, payload: { enabled: boolean; host: string; port: number; password?: string }) =>
    request<RconConfig>(`/api/servers/${id}/rcon/config`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  saveRconWelcomeConfig: (id: number, payload: { enabled: boolean; delaySeconds: number; messages: string[] }) =>
    request<RconWelcomeConfig>(`/api/servers/${id}/rcon/welcome`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  saveAutoRestartConfig: (id: number, payload: { enabled: boolean; time: string }) =>
    request<AutoRestartConfig>(`/api/servers/${id}/auto-restart`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  activity: (page = 0, size = 10) => request<PagedActivity>(`/api/activity?page=${page}&size=${size}`),
  profile: () => request<UserView>("/api/profile"),
  updateProfile: (payload: { displayName: string; email?: string }) =>
    request<UserView>("/api/profile", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  changePassword: (payload: { currentPassword: string; newPassword: string; confirmPassword: string }) =>
    request<{ message: string }>("/api/profile/password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  users: (params: { search?: string; role?: UserRole | ""; enabled?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.search) query.set("search", params.search);
    if (params.role) query.set("role", params.role);
    if (params.enabled) query.set("enabled", params.enabled);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return request<UserView[]>(`/api/admin/users${suffix}`);
  },
  createUser: (payload: {
    displayName: string;
    username: string;
    password: string;
    confirmPassword: string;
    email?: string;
    role: UserRole;
    enabled: boolean;
    mustChangePassword: boolean;
  }) =>
    request<UserView>("/api/admin/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  updateUser: (id: number, payload: { displayName: string; email?: string; role: UserRole; enabled: boolean; mustChangePassword: boolean }) =>
    request<UserView>(`/api/admin/users/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  resetUserPassword: (id: number, payload: { password: string; confirmPassword: string; mustChangePassword: boolean }) =>
    request<{ message: string }>(`/api/admin/users/${id}/reset-password`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  unlockUser: (id: number) => request<UserView>(`/api/admin/users/${id}/unlock`, { method: "POST" }),
  audit: (page = 0, size = 10) => request<PagedAudit>(`/api/admin/audit?page=${page}&size=${size}`)
  ,
  configProfiles: (serverId: number) => request<ConfigProfileList>(`/api/servers/${serverId}/config-profiles`),
  configProfile: (serverId: number, profileId: string) => request<ConfigProfileDetail>(`/api/servers/${serverId}/config-profiles/${profileId}`),
  configProfileDiff: (serverId: number, profileId: string) => request<ConfigProfileDiffEntry[]>(`/api/servers/${serverId}/config-profiles/${profileId}/diff`),
  createConfigProfile: (serverId: number, payload: { name: string; description: string }) =>
    request<ConfigProfileDetail>(`/api/servers/${serverId}/config-profiles`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  updateConfigProfile: (serverId: number, profileId: string, payload: { name: string; description: string; configuration?: string }) =>
    request<ConfigProfileDetail>(`/api/servers/${serverId}/config-profiles/${profileId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  duplicateConfigProfile: (serverId: number, profileId: string, payload: { name: string; description: string }) =>
    request<ConfigProfileDetail>(`/api/servers/${serverId}/config-profiles/${profileId}/duplicate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }),
  applyConfigProfile: (serverId: number, profileId: string) =>
    request<ConfigProfileApplyResult>(`/api/servers/${serverId}/config-profiles/${profileId}/apply`, { method: "POST" }),
  restoreDefaultConfigProfile: (serverId: number) =>
    request<ConfigProfileApplyResult>(`/api/servers/${serverId}/config-profiles/restore-default`, { method: "POST" }),
  deleteConfigProfile: (serverId: number, profileId: string) =>
    request<void>(`/api/servers/${serverId}/config-profiles/${profileId}`, { method: "DELETE" }),
  exportConfigProfile: (serverId: number, profileId: string) =>
    request<{ schemaVersion: number; name: string; description: string; configuration: string }>(`/api/servers/${serverId}/config-profiles/${profileId}/export`),
  importConfigProfile: (serverId: number, rawJson: string) =>
    request<ConfigProfileDetail>(`/api/servers/${serverId}/config-profiles/import`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: rawJson
    })
};
