import { EmptyState } from "../components/EmptyState";
import { SectionHeader } from "../components/SectionHeader";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";

export function SettingsPage() {
  return (
    <div>
      <SectionHeader title="Configuracion" description="Preferencias y estado de configuraciones disponibles en el panel moderno." />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Tema visual</CardTitle>
            <CardDescription>El selector del encabezado permite usar tema del sistema, claro u oscuro.</CardDescription>
          </CardHeader>
          <CardContent>
            <EmptyState title="Tema guardado localmente" description="La preferencia se conserva en este navegador." />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>RCON por servidor</CardTitle>
            <CardDescription>La configuracion de RCON se edita desde la vista Consola RCON para cada servidor.</CardDescription>
          </CardHeader>
          <CardContent>
            <EmptyState title="Sin secretos expuestos" description="El password RCON no se muestra despues de guardarlo." />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
