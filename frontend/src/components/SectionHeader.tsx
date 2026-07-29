export function SectionHeader({ title, description }: { title: string; description?: string }) {
  return (
    <div className="mb-5">
      <h1 className="text-2xl font-semibold tracking-normal sm:text-3xl">{title}</h1>
      {description ? <p className="mt-2 max-w-3xl text-sm text-muted-foreground">{description}</p> : null}
    </div>
  );
}
