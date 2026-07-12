import HealthCard from "@/components/HealthCard";

export default function DashboardPage() {
  return (
    <div>
      <h2 className="text-2xl font-semibold">Dashboard</h2>
      <p className="mt-1 text-sm text-gray-500">
        Visão geral do estado dos serviços monitorizados e alertas ativos.
      </p>
      <div className="mt-6 grid max-w-xl gap-4">
        <HealthCard />
        <div className="rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500 dark:border-gray-700">
          O estado dos serviços e os alertas ativos vão aparecer aqui
          (Módulos 4 e 6).
        </div>
      </div>
    </div>
  );
}
