"use client";

import { useEffect, useState } from "react";
import { 
    Table, 
    TableBody, 
    TableCell, 
    TableHead, 
    TableHeader, 
    TableRow 
} from "@/components/ui/table";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { 
    Select, 
    SelectContent, 
    SelectItem, 
    SelectTrigger, 
    SelectValue 
} from "@/components/ui/select";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { 
    Plus, 
    Trash2, 
    Edit2, 
    Scale, 
    ChefHat, 
    Search, 
    Save, 
    X, 
    RefreshCw, 
    AlertCircle, 
    CheckCircle2,
    Utensils
} from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios";

interface Ingredient {
    id: number;
    name: string;
    unit: string;
}

interface FoodIngredient {
    id: number;
    name: string;
    unit: string;
    amount: number;
}

interface Food {
    id: number;
    name: string;
    category_id: number;
    category_name: string;
    ingredients: FoodIngredient[];
}

export default function PortioningPage() {
    const [foods, setFoods] = useState<Food[]>([]);
    const [ingredients, setIngredients] = useState<Ingredient[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState("");
    const [selectedCategory, setSelectedCategory] = useState("all");
    const [activeTab, setActiveTab] = useState("all");
    
    const [selectedFood, setSelectedFood] = useState<Food | null>(null);
    const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
    const [editingIngredients, setEditingIngredients] = useState<any[]>([]);

    useEffect(() => {
        fetchData();
    }, []);

    // Extract unique categories for the filter
    const categories = Array.from(new Set(foods.map(f => f.category_name).filter(Boolean)));

    const fetchData = async () => {
        try {
            setLoading(true);
            const [foodsRes, spareRes] = await Promise.all([
                api.get("/menu/foods"),
                api.get("/inventory/ingredients")
            ]);
            setFoods(foodsRes.data);
            setIngredients(spareRes.data);
        } catch (error) {
            console.error("Lỗi tải dữ liệu:", error);
            toast.error("Không thể tải dữ liệu định lượng");
        } finally {
            setLoading(false);
        }
    };

    const handleEditPortion = (food: Food) => {
        setSelectedFood(food);
        setEditingIngredients(food.ingredients.map(i => ({
            id: i.id,
            amount: i.amount
        })));
        setIsEditDialogOpen(true);
    };

    const addIngredientRow = () => {
        setEditingIngredients([...editingIngredients, { id: "", amount: 1 }]);
    };

    const removeIngredientRow = (index: number) => {
        const updated = [...editingIngredients];
        updated.splice(index, 1);
        setEditingIngredients(updated);
    };

    const updateIngredientRow = (index: number, field: string, value: any) => {
        const updated = [...editingIngredients];
        updated[index][field] = value;
        setEditingIngredients(updated);
    };

    const handleSavePortion = async () => {
        if (!selectedFood) return;

        const hasEmpty = editingIngredients.some(i => !i.id || i.amount <= 0);
        if (hasEmpty) {
            toast.error("Vui lòng nhập đầy đủ nguyên liệu và khối lượng > 0");
            return;
        }

        try {
            await api.put(`/menu/foods/${selectedFood.id}`, {
                ingredients: editingIngredients.map(i => ({
                    id: parseInt(i.id),
                    amount: parseFloat(i.amount)
                }))
            });
            toast.success("Đã cập nhật định lượng thành công");
            setIsEditDialogOpen(false);
            fetchData();
        } catch (error) {
            console.error("Lỗi lưu định lượng:", error);
            toast.error("Không thể lưu định lượng");
        }
    };

    const filteredFoods = foods.filter(f => {
        const matchesSearch = f.name.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesCategory = selectedCategory === "all" || f.category_name === selectedCategory;
        
        const matchesTab = activeTab === "all" || (activeTab === "missing" && (!f.ingredients || f.ingredients.length === 0));

        return matchesSearch && matchesCategory && matchesTab;
    });

    const stats = {
        total: foods.length,
        configured: foods.filter(f => f.ingredients && f.ingredients.length > 0).length,
        missing: foods.filter(f => !f.ingredients || f.ingredients.length === 0).length,
    };

    return (
        <div className="space-y-6">
            {/* Stats Cards */}
            <div className="grid gap-4 md:grid-cols-3">
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Tổng món ăn</CardTitle>
                        <Utensils className="size-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{loading ? "..." : stats.total}</div>
                        <p className="text-xs text-muted-foreground">món trong menu</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Đã định lượng</CardTitle>
                        <CheckCircle2 className="size-4 text-green-500" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold text-green-600">{loading ? "..." : stats.configured}</div>
                        <p className="text-xs text-muted-foreground">đã thiết lập công thức</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Chưa định lượng</CardTitle>
                        <AlertCircle className="size-4 text-orange-500" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold text-orange-600">{loading ? "..." : stats.missing}</div>
                        <p className="text-xs text-muted-foreground">cần bổ sung công thức</p>
                    </CardContent>
                </Card>
            </div>

            {/* Main Content with Tabs */}
            <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
                <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                    <TabsList>
                        <TabsTrigger value="all">
                            <Scale className="mr-2 size-4" />
                            Tất cả ({stats.total})
                        </TabsTrigger>
                        <TabsTrigger value="missing">
                            <AlertCircle className="mr-2 size-4" />
                            Chưa thiết lập ({stats.missing})
                        </TabsTrigger>
                    </TabsList>

                    <div className="flex flex-wrap gap-2">
                        <Select value={selectedCategory} onValueChange={setSelectedCategory}>
                            <SelectTrigger className="w-[200px]">
                                <SelectValue placeholder="Tất cả danh mục" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="all">Tất cả danh mục</SelectItem>
                                {categories.map(cat => (
                                    <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                                ))}
                            </SelectContent>
                        </Select>

                        <div className="relative flex-1 md:w-64">
                            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                            <Input
                                placeholder="Tìm kiếm món ăn..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="pl-9"
                            />
                        </div>
                        <Button variant="outline" size="icon" onClick={fetchData}>
                            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
                        </Button>
                    </div>
                </div>

                <TabsContent value="all" className="space-y-4">
                    <PortioningTable 
                        foods={filteredFoods} 
                        loading={loading} 
                        onEdit={handleEditPortion} 
                    />
                </TabsContent>

                <TabsContent value="missing" className="space-y-4">
                    <PortioningTable 
                        foods={filteredFoods} 
                        loading={loading} 
                        onEdit={handleEditPortion} 
                    />
                </TabsContent>
            </Tabs>

            {/* Edit Dialog - Enhanced styling */}
            <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
                <DialogContent className="max-w-2xl sm:max-h-[90vh] overflow-hidden flex flex-col">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2 text-xl">
                            <ChefHat className="h-6 w-6 text-primary" />
                            Công thức: {selectedFood?.name}
                        </DialogTitle>
                        <DialogDescription>
                            Thiết lập nguyên liệu tiêu hao cho 1 phần ăn. Hệ thống sẽ tự động trừ kho khi món hoàn thành.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="flex-1 overflow-y-auto py-4 space-y-4">
                        <div className="flex justify-between items-center sticky top-0 bg-background z-10 pb-2">
                            <Label className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">Danh sách nguyên liệu</Label>
                            <Button type="button" variant="outline" size="sm" onClick={addIngredientRow} className="h-8">
                                <Plus className="h-3.5 w-3.5 mr-1" /> Thêm dòng
                            </Button>
                        </div>

                        <div className="space-y-3">
                            {editingIngredients.map((row, index) => (
                                <div key={index} className="flex gap-3 items-start border p-3 rounded-lg bg-muted/20 relative group transition-colors hover:bg-muted/40">
                                    <div className="flex-1 space-y-1.5">
                                        <Label className="text-xs">Nguyên liệu</Label>
                                        <Select 
                                            value={row.id.toString()} 
                                            onValueChange={(val) => updateIngredientRow(index, "id", val)}
                                        >
                                            <SelectTrigger className="h-9">
                                                <SelectValue placeholder="Chọn nguyên liệu" />
                                            </SelectTrigger>
                                            <SelectContent>
                                                {ingredients.map(ing => (
                                                    <SelectItem key={ing.id} value={ing.id.toString()}>
                                                        {ing.name} ({ing.unit})
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                    </div>
                                    <div className="w-32 space-y-1.5">
                                        <Label className="text-xs">Khối lượng</Label>
                                        <div className="relative">
                                            <Input
                                                type="number"
                                                min="0.001"
                                                step="0.001"
                                                value={row.amount}
                                                onChange={(e) => updateIngredientRow(index, "amount", e.target.value)}
                                                className="h-9 pr-9"
                                            />
                                            <span className="absolute right-2.5 top-2 text-xs font-medium text-muted-foreground">
                                                {ingredients.find(i => i.id.toString() === row.id.toString())?.unit || ""}
                                            </span>
                                        </div>
                                    </div>
                                    <div className="pt-7">
                                        <Button 
                                            variant="ghost" 
                                            size="icon" 
                                            className="h-9 w-9 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
                                            onClick={() => removeIngredientRow(index)}
                                        >
                                            <Trash2 className="h-4 w-4" />
                                        </Button>
                                    </div>
                                </div>
                            ))}
                            {editingIngredients.length === 0 && (
                                <div className="text-center py-10 border-2 border-dashed rounded-xl text-muted-foreground flex flex-col items-center gap-2">
                                    <Scale className="h-10 w-10 opacity-20" />
                                    <p>Chưa có định lượng nào cho món ăn này.</p>
                                    <Button variant="link" onClick={addIngredientRow}>Nhấn để thêm nguyên liệu đầu tiên</Button>
                                </div>
                            )}
                        </div>
                    </div>

                    <DialogFooter className="pt-4 border-t">
                        <Button variant="outline" onClick={() => setIsEditDialogOpen(false)}>
                            <X className="h-4 w-4 mr-2" /> Hủy
                        </Button>
                        <Button onClick={handleSavePortion} className="min-w-[140px]">
                            <Save className="h-4 w-4 mr-2" /> Lưu công thức
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}

function PortioningTable({ foods, loading, onEdit }: { foods: Food[], loading: boolean, onEdit: (food: Food) => void }) {
    if (loading) {
        return (
            <div className="flex items-center justify-center py-20">
                <RefreshCw className="size-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    if (foods.length === 0) {
        return (
            <Card>
                <CardContent className="flex flex-col items-center justify-center py-20">
                    <ChefHat className="mb-4 size-16 text-muted-foreground opacity-20" />
                    <p className="text-xl font-medium text-muted-foreground">Không tìm thấy món ăn nào</p>
                    <p className="text-sm text-muted-foreground">Thử tìm kiếm với từ khóa khác</p>
                </CardContent>
            </Card>
        );
    }

    return (
        <Card className="overflow-hidden border-none shadow-md">
            <CardContent className="p-0">
                <Table>
                    <TableHeader className="bg-muted/50">
                        <TableRow>
                            <TableHead className="w-[80px]">ID</TableHead>
                            <TableHead className="min-w-[200px]">Tên món ăn</TableHead>
                            <TableHead>Danh mục</TableHead>
                            <TableHead className="max-w-[400px]">Nguyên liệu tiêu hao</TableHead>
                            <TableHead className="text-right">Thao tác</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {foods.map((food) => (
                            <TableRow key={food.id} className="hover:bg-muted/30 transition-colors">
                                <TableCell className="font-mono text-xs text-muted-foreground">#{food.id}</TableCell>
                                <TableCell className="font-semibold">{food.name}</TableCell>
                                <TableCell>
                                    <Badge variant="secondary" className="font-normal capitalize">{food.category_name}</Badge>
                                </TableCell>
                                <TableCell>
                                    <div className="flex flex-wrap gap-1.5">
                                        {food.ingredients && food.ingredients.length > 0 ? (
                                            food.ingredients.map((ing, idx) => (
                                                <Badge key={idx} variant="outline" className="bg-primary/5 text-[11px] h-6 border-primary/20">
                                                    {ing.name}: <span className="ml-1 font-bold">{ing.amount}{ing.unit}</span>
                                                </Badge>
                                            ))
                                        ) : (
                                            <Badge variant="destructive" className="bg-orange-100 text-orange-700 hover:bg-orange-100 border-none font-medium text-[10px] h-5">
                                                CHƯA THIẾT LẬP
                                            </Badge>
                                        )}
                                    </div>
                                </TableCell>
                                <TableCell className="text-right">
                                    <Button 
                                        variant="ghost" 
                                        size="sm" 
                                        onClick={() => onEdit(food)}
                                        className="h-8 w-8 p-0"
                                    >
                                        <Edit2 className="h-4 w-4" />
                                        <span className="sr-only">Sửa</span>
                                    </Button>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    );
}
