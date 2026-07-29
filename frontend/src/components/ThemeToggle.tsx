import { Monitor, Moon, Sun } from "lucide-react";
import type { ThemeMode } from "../lib/theme";

type ThemeToggleProps = {
  value: ThemeMode;
  onChange: (value: ThemeMode) => void;
};

const options: Array<{ value: ThemeMode; label: string; icon: typeof Monitor }> = [
  { value: "system", label: "Sistema", icon: Monitor },
  { value: "light", label: "Claro", icon: Sun },
  { value: "dark", label: "Oscuro", icon: Moon }
];

export function ThemeToggle({ value, onChange }: ThemeToggleProps) {
  return (
    <div className="flex items-center rounded-md border border-border bg-card p-1" aria-label="Tema visual">
      {options.map((option) => {
        const Icon = option.icon;
        const active = value === option.value;
        return (
          <button
            key={option.value}
            type="button"
            title={`Tema ${option.label}`}
            aria-label={`Tema ${option.label}`}
            aria-pressed={active}
            onClick={() => onChange(option.value)}
            className={[
              "flex h-8 items-center gap-1.5 rounded px-2 text-xs transition",
              active ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
            ].join(" ")}
          >
            <Icon className="h-3.5 w-3.5" />
            <span className="hidden xl:inline">{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}
