"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Plus, Pencil, Trash2, UtensilsCrossed, ToggleLeft, ToggleRight } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios";
import { getUser, getDefaultPath } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Checkbox } from "@/components/ui/checkbox";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";

interface FoodDto {
    id: number;
    name: string;
    price: number;
    imageUrl?: string;
    category_id?: number;
    categoryId?: number;
    categoryName?: string;
}

interface BuffetPackageDto {
    id?: number;
    name: string;
    price: number;
    description?: string;
    isActive: boolean;
    foodIds: number[];
    foods?: FoodDto[];
}

const emptyForm: BuffetPackageDto = {
    name: "",
    price: 0,
    description: "",
    isActive: true,
    foodIds: [],
};

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);

export default function BuffetPage() {
    const router = useRouter();
    const [packages, setPackages] = useState<BuffetPackageDto[]>([]);
    const [allFoods, setAllFoods] = useState<FoodDto[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [editingPackage, setEditingPackage] = useState<BuffetPackageDto | null>(null);
    const [deletingId, setDeletingId] = useState<number | null>(null);
    const [form, setForm] = useState<BuffetPackageDto>(emptyForm);
    const [isSaving, setIsSaving] = useState(false);
    const [foodSearch, setFoodSearch] = useState("");

    const loadData = useCallback(async () => {
        setIsLoading(true);
        try {            const [pkgRes, foodRes] = await Promise.all([
                api.get("/menu/buffet-packages"),
                api.get("/menu/foods"),
            ]);
            setPackages(pkgRes.data || []);
            setAllFoods(foodRes.data || []);
        } catch {
            toast.error("Không tải được dữ liệu");
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        // Phân quyền: chỉ ADMIN và MANAGER được vào trang này
        const user = getUser();
        const role = user?.roleName?.toUpperCase();
        if (!user || (role !== "ADMIN" && role !== "MANAGER")) {
            router.replace(getDefaultPath(user?.roleName));
            return;
        }
        loadData();
    }, [loadData, router]);

    const openCreate = () => {
        setEditingPackage(null);
        setForm(emptyForm);
        setFoodSearch("");
        setDialogOpen(true);
    };

    const openEdit = (pkg: BuffetPackageDto) => {
        setEditingPackage(pkg);
        setForm({
            name: pkg.name,
            price: pkg.price,
            description: pkg.description || "",
            isActive: pkg.isActive,
            foodIds: pkg.foodIds || [],
        });
        setFoodSearch("");
        setDialogOpen(true);
    };

    const toggleFood = (foodId: number) => {
        setForm((prev) => ({
            ...prev,
            foodIds: prev.foodIds.includes(foodId)
                ? prev.foodIds.filter((id) => id !== foodId)
                : [...prev.foodIds, foodId],
        }));
    };

    const handleSave = async () => {
        if (!form.name.trim()) { toast.error("Vui lòng nhập tên gói"); return; }
        if (form.price <= 0) { toast.error("Giá gói phải lớn hơn 0"); return; }

        setIsSaving(true);
        try {
            if (editingPackage?.id) {
                await api.put(`/menu/buffet-packages/${editingPackage.id}`, form);
                toast.success("Đã cập nhật gói buffet");
            } else {
                await api.post("/menu/buffet-packages", form);
                toast.success("Đã tạo gói buffet mới");
            }
            setDialogOpen(false);
            loadData();
        } catch (error: any) {
            toast.error("Lưu thất bại", { description: error.response?.data?.message || error.message });
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!deletingId) return;
        try {
            await api.delete(`/menu/buffet-packages/${deletingId}`);
            toast.success("Đã xóa gói buffet");
            setDeleteDialogOpen(false);
            setDeletingId(null);
            loadData();
        } catch (error: any) {
            toast.error("Xóa thất bại", { description: error.response?.data?.message || error.message });
        }
    };

    // Group foods by category for display
    const categorizedFoods = allFoods.reduce((acc, food) => {
        const catName = food.categoryName || "Không phân loại";
        if (!acc[catName]) acc[catName] = [];
        acc[catName].push(food);
        return acc;
    }, {} as Record<string, FoodDto[]>);

    const filteredCategorizedFoods = Object.entries(categorizedFoods).reduce((acc, [cat, foods]) => {
        const filtered = foods.filter((f) =>
            f.name.toLowerCase().includes(foodSearch.toLowerCase())
        );
        if (filtered.length > 0) acc[cat] = filtered;
        return acc;
    }, {} as Record<string, FoodDto[]>);

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-3xl font-bold tracking-tight">Quản lý Gói Buffet</h2>
                    <p className="text-muted-foreground mt-1">Tạo và quản lý các gói buffet, chọn món áp dụng cho từng gói</p>
                </div>
                <Button onClick={openCreate} className="gap-2">
                    <Plus className="size-4" />
                    Tạo gói mới
                </Button>
            </div>

            <Card>
                <CardHeader>
                    <CardTitle>Danh sách gói buffet</CardTitle>
                    <CardDescription>Các gói buffet đang được cấu hình trong hệ thống</CardDescription>
                </CardHeader>
                <CardContent>
                    {isLoading ? (
                        <div className="space-y-3">
                            {[1, 2, 3].map((i) => <Skeleton key={i} className="h-14 w-full" />)}
                        </div>
                    ) : packages.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
                            <UtensilsCrossed className="size-12 mb-4 opacity-30" />
                            <p className="text-lg font-medium text-foreground">Chưa có gói buffet nào</p>
                            <p className="text-sm mt-1">Nhấn "Tạo gói mới" để bắt đầu</p>
                        </div>
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Tên gói</TableHead>
                                    <TableHead>Giá</TableHead>
                                    <TableHead>Số món</TableHead>
                                    <TableHead>Trạng thái</TableHead>
                                    <TableHead className="text-right">Thao tác</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {packages.map((pkg) => (
                                    <TableRow key={pkg.id}>
                                        <TableCell>
                                            <div>
                                                <p className="font-medium">{pkg.name}</p>
                                                {pkg.description && (
                                                    <p className="text-sm text-muted-foreground truncate max-w-xs">{pkg.description}</p>
                                                )}
                                            </div>
                                        </TableCell>
                                        <TableCell className="font-semibold text-green-600">
                                            {formatCurrency(pkg.price)}
                                        </TableCell>
                                        <TableCell>
                                            <Badge variant="outline">{pkg.foodIds?.length || 0} món</Badge>
                                        </TableCell>
                                        <TableCell>
                                            <Badge variant={pkg.isActive ? "default" : "secondary"}>
                                                {pkg.isActive ? "Đang hoạt động" : "Tạm dừng"}
                                            </Badge>
                                        </TableCell>
                                        <TableCell className="text-right">
                                            <div className="flex justify-end gap-2">
                                                <Button variant="outline" size="sm" onClick={() => openEdit(pkg)}>
                                                    <Pencil className="size-4" />
                                                </Button>
                                                <Button
                                                    variant="destructive"
                                                    size="sm"
                                                    onClick={() => { setDeletingId(pkg.id!); setDeleteDialogOpen(true); }}
                                                >
                                                    <Trash2 className="size-4" />
                                                </Button>
                                            </div>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            {/* Create/Edit Dialog */}
            <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
                <DialogContent className="max-w-2xl max-h-[90vh] flex flex-col">
                    <DialogHeader>
                        <DialogTitle>
                            {editingPackage ? "Chỉnh sửa gói buffet" : "Tạo gói buffet mới"}
                        </DialogTitle>
                        <DialogDescription>
                            Điền thông tin gói buffet và chọn các món áp dụng.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="flex-1 overflow-y-auto space-y-4 pr-1">
                        {/* Basic info */}
                        <div className="grid grid-cols-2 gap-4">
                            <div className="col-span-2 space-y-1.5">
                                <Label htmlFor="pkg-name">Tên gói *</Label>
                                <Input
                                    id="pkg-name"
                                    value={form.name}
                                    onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
                                    placeholder="Vd: Buffet Tiêu Chuẩn, Buffet VIP..."
                                />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="pkg-price">Giá (VNĐ) *</Label>
                                <Input
                                    id="pkg-price"
                                    type="number"
                                    value={form.price}
                                    onChange={(e) => setForm((p) => ({ ...p, price: Number(e.target.value) }))}
                                    placeholder="299000"
                                />
                            </div>
                            <div className="flex items-center gap-3 pt-6">
                                <Switch
                                    id="pkg-active"
                                    checked={form.isActive}
                                    onCheckedChange={(v) => setForm((p) => ({ ...p, isActive: v }))}
                                />
                                <Label htmlFor="pkg-active">
                                    {form.isActive ? "Đang hoạt động" : "Tạm dừng"}
                                </Label>
                            </div>
                            <div className="col-span-2 space-y-1.5">
                                <Label htmlFor="pkg-desc">Mô tả</Label>
                                <Textarea
                                    id="pkg-desc"
                                    value={form.description}
                                    onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))}
                                    placeholder="Mô tả ngắn về gói buffet..."
                                    rows={2}
                                />
                            </div>
                        </div>

                        {/* Food selection */}
                        <div className="space-y-2">
                            <Label>Chọn món áp dụng ({form.foodIds.length} món đã chọn)</Label>
                            <Input
                                placeholder="Tìm tên món..."
                                value={foodSearch}
                                onChange={(e) => setFoodSearch(e.target.value)}
                            />
                            <ScrollArea className="h-64 border rounded-md p-3">
                                {Object.entries(filteredCategorizedFoods).length === 0 ? (
                                    <p className="text-center text-muted-foreground text-sm py-8">Không tìm thấy món nào</p>
                                ) : (
                                    Object.entries(filteredCategorizedFoods).map(([cat, foods]) => (
                                        <div key={cat} className="mb-4">
                                            <p className="text-xs font-semibold text-muted-foreground uppercase mb-2 border-b pb-1">
                                                {cat}
                                            </p>
                                            <div className="space-y-1.5">
                                                {foods.map((food) => (
                                                    <div key={food.id} className="flex items-center gap-2">
                                                        <Checkbox
                                                            id={`food-${food.id}`}
                                                            checked={form.foodIds.includes(food.id)}
                                                            onCheckedChange={() => toggleFood(food.id)}
                                                        />
                                                        <label
                                                            htmlFor={`food-${food.id}`}
                                                            className="flex-1 text-sm cursor-pointer flex justify-between"
                                                        >
                                                            <span>{food.name}</span>
                                                            <span className="text-muted-foreground">{formatCurrency(food.price)}</span>
                                                        </label>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    ))
                                )}
                            </ScrollArea>
                        </div>
                    </div>

                    <DialogFooter className="mt-4">
                        <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={isSaving}>
                            Hủy
                        </Button>
                        <Button onClick={handleSave} disabled={isSaving}>
                            {isSaving ? "Đang lưu..." : editingPackage ? "Lưu thay đổi" : "Tạo gói"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Delete confirmation */}
            <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Xác nhận xóa?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Xóa gói buffet này sẽ xóa cả danh sách món đã cấu hình. Hành động này không thể hoàn tác.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel onClick={() => setDeletingId(null)}>Hủy</AlertDialogCancel>
                        <AlertDialogAction onClick={handleDelete} className="bg-destructive hover:bg-destructive/90">
                            Xóa
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
