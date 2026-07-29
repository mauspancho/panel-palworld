import * as React from "react";
import { applyTheme, type ThemeMode } from "../lib/theme";

export function useTheme() {
  const [theme, setThemeState] = React.useState<ThemeMode>("dark");

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

  return { theme, setTheme: setThemeState };
}
