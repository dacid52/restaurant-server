import { AdminLayout } from "@/components/admin-layout";

export default function TablesLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AdminLayout>{children}</AdminLayout>;
}
