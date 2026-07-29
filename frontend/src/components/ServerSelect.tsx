import type { ServerView } from "../types";

type ServerSelectProps = {
  servers: ServerView[];
  value: number | null;
  onChange: (id: number) => void;
};

export function ServerSelect({ servers, value, onChange }: ServerSelectProps) {
  return (
    <select
      className="focus-ring h-10 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground"
      value={value ?? servers[0]?.id ?? ""}
      onChange={(event) => onChange(Number(event.target.value))}
    >
      {servers.map((server) => (
        <option key={server.id} value={server.id}>
          {server.name}
        </option>
      ))}
    </select>
  );
}
