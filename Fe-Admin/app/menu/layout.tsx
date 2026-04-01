import { AdminLayout } from "@/components/admin-layout";
import { Toaster } from "@/components/ui/sonner";

export default function MenuLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    return (
        <AdminLayout>
            {children}
            <Toaster position="top-right" richColors />
        </AdminLayout>
    );
}
