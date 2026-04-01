import { FloorPlanBuilder } from "@/components/floor-plan";
import { AdminLayout } from "@/components/admin-layout";

export default function FloorPlanPage() {
  return (
    <AdminLayout>
      <div className="flex-1 w-full h-[calc(100vh-4rem)]">
        <FloorPlanBuilder />
      </div>
    </AdminLayout>
  );
}
