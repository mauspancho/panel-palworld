export type ServerStatus = "RUNNING" | "STOPPED" | "RESTARTING" | "ERROR" | "UNKNOWN";

export type ServerView = {
  id: number;
  name: string;
  type: string;
  serviceName: string | null;
  containerName: string | null;
  rootPath: string;
  status: ServerStatus;
  statusLabel: string;
  publicPort: number | null;
  rconEnabled: boolean;
  rconPort: number | null;
  enabled: boolean;
};

export type DashboardStats = {
  totalServers: number;
  runningServers: number;
  stoppedServers: number;
  errorServers: number;
  rconEnabledServers: number;
};

export type ActivityItem = {
  startedAt: string | null;
  serverName: string | null;
  action: string | null;
  status: string | null;
  username: string | null;
};

export type ActivityPoint = {
  date: string;
  actions: number;
  players?: string[];
};

export type PageView = {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type DashboardView = {
  servers: ServerView[];
  stats: DashboardStats;
  recentActivity: ActivityItem[];
  activitySeries: ActivityPoint[];
  page: PageView;
};

export type UserSession = {
  username: string;
  displayName: string;
  email: string;
  role: UserRole;
  roles: string[];
  enabled: boolean;
  locked: boolean;
  mustChangePassword: boolean;
};

export type UserRole = "ADMIN" | "USER";

export type UserView = {
  id: number;
  displayName: string;
  username: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  locked: boolean;
  mustChangePassword: boolean;
  createdAt: string | null;
  lastLoginAt: string | null;
  passwordChangedAt: string | null;
  createdBy: string | null;
};

export type AuditItem = {
  createdAt: string;
  actorUsername: string;
  targetUsername: string;
  action: string;
  status: string;
  description: string;
};

export type PagedAudit = {
  items: AuditItem[];
  page: PageView;
};

export type RconPlayer = {
  name: string;
  playerId: string;
  platformId: string;
  raw: string;
};

export type RconPlayersView = {
  serverId: number;
  serverName: string;
  enabled: boolean;
  success: boolean;
  message: string;
  players: RconPlayer[];
  raw: string;
  refreshedAt: string;
};

export type RconConfig = {
  serverId: number;
  serverName: string;
  enabled: boolean;
  host: string;
  port: number;
  passwordConfigured: boolean;
};

export type RconWelcomeConfig = {
  serverId: number;
  serverName: string;
  enabled: boolean;
  delaySeconds: number;
  messages: string[];
};

export type AutoRestartConfig = {
  serverId: number;
  serverName: string;
  enabled: boolean;
  time: string | null;
  warningMinutes: number;
  lastWarningDate: string | null;
  lastRunDate: string | null;
};

export type PlayerAnalyticsRange = "day" | "week" | "month";

export type PlayerAverageView = {
  range: PlayerAnalyticsRange;
  label: string;
  averagePlayers: number;
  peakPlayers: number;
  sampleCount: number;
  startedAt: string;
  endedAt: string;
};

export type PlayerDurationPoint = {
  bucket: string;
  minutes: number;
  hours: number;
};

export type PlayerDurationView = {
  key: string;
  name: string;
  playerId: string | null;
  platformId: string | null;
  totalMinutes: number;
  totalHours: number;
  series: PlayerDurationPoint[];
  sessions: PlayerSessionView[];
};

export type PlayerSessionView = {
  playerKey?: string;
  playerName?: string;
  startedAt: string;
  endedAt: string | null;
  active?: boolean;
  durationSeconds?: number;
  minutes?: number;
  hours: number;
};

export type PlayerDurationAnalytics = {
  range: PlayerAnalyticsRange;
  label: string;
  snapshotMinutes: number;
  players: PlayerDurationView[];
};

export type RegisteredPlayerView = {
  key: string;
  name: string;
  playerId: string | null;
  platformId: string | null;
  active: boolean;
  firstSeenAt: string | null;
  lastSeenAt: string | null;
  lastConnectedAt: string | null;
  lastDisconnectedAt: string | null;
  totalSeconds: number;
  totalHours: number;
  sessions: PlayerSessionView[];
};

export type PlayerRegistryView = {
  serverId: number;
  serverName: string;
  range: PlayerAnalyticsRange;
  label: string;
  totalPlayers: number;
  activePlayers: number;
  inactivePlayers: number;
  players: RegisteredPlayerView[];
  sessions: PlayerSessionView[];
};

export type ActionResult = {
  success: boolean;
  message: string;
  output: string;
  error: string;
};

export type PagedActivity = {
  items: ActivityItem[];
  page: PageView;
};

export type InternalServerLog = {
  startedAt: string | null;
  finishedAt: string | null;
  action: string | null;
  status: string | null;
  message: string | null;
  error: string | null;
  username: string | null;
};

export type ServerLogsView = {
  serverId: number;
  serverName: string;
  lines: number;
  success: boolean;
  combinedOutput: string;
  output: string;
  error: string;
  internalLogs: InternalServerLog[];
};

export type ConfigProfileSummary = {
  id: string;
  name: string;
  description: string;
  isDefault: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  updatedBy: string | null;
  hash: string;
  parameterCount: number;
};

export type ConfigProfileList = {
  serverId: number;
  serverName: string;
  activeProfileId: string | null;
  defaultProfileId: string;
  externalModified: boolean;
  profiles: ConfigProfileSummary[];
};

export type ConfigProfileDetail = ConfigProfileSummary & {
  configuration: string;
};

export type ConfigProfileDiffEntry = {
  key: string;
  previousValue: string | null;
  newValue: string | null;
};

export type ConfigProfileApplyResult = {
  success: boolean;
  message: string;
  profileId: string;
  backupPath: string;
  changes: ConfigProfileDiffEntry[];
};
