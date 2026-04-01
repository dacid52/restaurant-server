"use client";

import React, { useState, useEffect } from "react";
import dynamic from "next/dynamic";
import { Plus, Maximize, MousePointer2, Settings, Edit3, Type, Square, Save, EyeOff, Eye } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { toast } from "sonner";
import api from "@/lib/axios";
import type { PlanObject } from "./floor-plan-canvas";

// Dynamically import the Canvas so it doesn't crash on SSR
const FloorPlanCanvas = dynamic(() => import("./floor-plan-canvas"), {
  ssr: false,
  loading: () => (
    <div className="w-full h-full flex flex-col items-center justify-center bg-slate-50 text-muted-foreground">
      <div className="animate-pulse w-16 h-16 bg-slate-200 rounded-full mb-4"></div>
      <p>Loading Canvas Engine...</p>
    </div>
  ),
});

interface Floor {
  id: string;
  name: string;
  objects: PlanObject[];
}

export function FloorPlanBuilder({ 
  mode = "edit",
  onTableClick
}: { 
  mode?: "view" | "edit";
  onTableClick?: (tableId: string) => void;
}) {
  const [floors, setFloors] = useState<Floor[]>([
    { id: "floor-1", name: "Tầng 1 (Trệt)", objects: [] },
  ]);
  const [currentFloorId, setCurrentFloorId] = useState("floor-1");
  const [editMode, setEditMode] = useState(mode === "edit");
  const [drawMode, setDrawMode] = useState<"none" | "wall">("none");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // Load from API / local storage
  useEffect(() => {
    const loadData = async () => {
      try {
        const response = await api.get('/tables/layouts');
        const parsed = response.data;
        if (parsed && Array.isArray(parsed) && parsed.length > 0) {
          setFloors(parsed);
          setCurrentFloorId((prev) => 
            parsed.some((f: Floor) => f.id === prev) ? prev : parsed[0].id
          );
        } else {
          // Fallback to local storage if API is empty
          const saved = localStorage.getItem("restaurant_layout");
          if (saved) {
            const lsParsed = JSON.parse(saved);
            if (lsParsed && lsParsed.length > 0) {
              setFloors(lsParsed);
            }
          }
        }
      } catch (e) {
        console.error("Failed to fetch layout from server", e);
      }
    };

    loadData();
    window.addEventListener("layout-updated", loadData);
    return () => window.removeEventListener("layout-updated", loadData);
  }, []);

  // Sync editMode prop
  useEffect(() => {
    setEditMode(mode === "edit");
  }, [mode]);

  // Sync objects of the current floor
  const currentFloorIndex = floors.findIndex((f) => f.id === currentFloorId);
  const currentFloor = floors[currentFloorIndex] || floors[0];

  // Helper to update objects for current floor
  const updateObjects = (newObjects: PlanObject[] | ((prev: PlanObject[]) => PlanObject[])) => {
    if (currentFloorIndex === -1) return;
    setFloors((prev) => {
      const nextFloors = [...prev];
      const items = typeof newObjects === "function" ? newObjects(nextFloors[currentFloorIndex].objects) : newObjects;
      nextFloors[currentFloorIndex] = { ...nextFloors[currentFloorIndex], objects: items };
      // auto save
      localStorage.setItem("restaurant_layout", JSON.stringify(nextFloors));
      return nextFloors;
    });
  };

  const addTable = () => {
    const newTable: PlanObject = {
      id: `table-${Date.now()}`,
      type: "table",
      name: `Bàn ${currentFloor.objects.filter(o => o.type === "table").length + 1}`,
      x: 100,
      y: 100,
      width: 100,
      height: 100,
      rotation: 0,
      status: "available",
      color: "#10B981"
    };
    updateObjects([...currentFloor.objects, newTable]);
    setDrawMode("none");
    setSelectedId(newTable.id);
  };

  const addZone = () => {
    const newZone: PlanObject = {
      id: `zone-${Date.now()}`,
      type: "zone",
      name: `Khu vực ${currentFloor.objects.filter(o => o.type === "zone").length + 1}`,
      x: 100,
      y: 100,
      width: 300,
      height: 200,
      rotation: 0,
      color: "rgba(59, 130, 246, 0.15)"
    };
    updateObjects([newZone, ...currentFloor.objects]); // Zone goes behind
    setDrawMode("none");
    setSelectedId(newZone.id);
  };

  const toggleWallMode = () => {
    if (drawMode === "wall") {
      setDrawMode("none");
      toast.info("Đã tắt chế độ vẽ tường");
    } else {
      setDrawMode("wall");
      setSelectedId(null);
      toast.info("Chế độ vẽ tường: Click điểm đầu, click điểm nối tiếp, Double-click để kết thúc!");
    }
  };

  const handleSave = async () => {
    try {
      await api.post('/tables/layouts', floors);
      toast.success("Áp dụng thành công! Danh sách bàn đã lưu lên máy chủ và cập nhật.");
      window.dispatchEvent(new Event("layout-updated"));
    } catch (err) {
      toast.error("Lỗi khi lưu sơ đồ lên máy chủ!");
      console.error(err);
    }
  };

  const deleteSelected = () => {
    if (!selectedId) return;
    updateObjects((prev) => prev.filter((o) => o.id !== selectedId));
    setSelectedId(null);
    toast.success("Đã xóa đối tượng");
  };

  // Keyboard shortcut for Delete
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Delete" || e.key === "Backspace") {
        // Prevent deleting if typing in an input
        if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
        deleteSelected();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [selectedId, currentFloor]);

  return (
    <div className="flex h-full w-full overflow-hidden relative bg-slate-50">
      
      {/* Sidebar Tool panel */}
      {mode === "edit" && (
      <div className="w-64 border-r bg-card flex flex-col shadow-sm z-10">
        <div className="p-4 border-b">
          <h2 className="font-semibold text-lg flex items-center gap-2">
            <Maximize className="w-5 h-5 text-primary" />
            Floor Builder
          </h2>
          <p className="text-xs text-muted-foreground mt-1">
            Kéo thả để thiết kế vị trí bàn & khu vực nhà hàng
          </p>
        </div>

        <div className="p-4 flex-1 space-y-6 overflow-y-auto">
          {/* Objects Palette */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold uppercase text-muted-foreground tracking-wider">
              Thêm Vật Thể
            </h3>
            <div className="grid grid-cols-2 gap-2">
              <Button
                variant="outline"
                className="flex flex-col items-center h-20 bg-emerald-50 hover:bg-emerald-100 border-none justify-center gap-2 dark:bg-emerald-950"
                onClick={addTable}
                disabled={!editMode}
              >
                <div className="w-8 h-8 rounded-lg bg-emerald-500 shadow-md"></div>
                <span className="text-xs">Bàn tròn/vuông</span>
              </Button>
              
              <Button
                variant={drawMode === "wall" ? "secondary" : "outline"}
                className="flex flex-col items-center h-20 justify-center gap-2"
                onClick={toggleWallMode}
                disabled={!editMode}
              >
                <Edit3 className="w-6 h-6 text-slate-700 dark:text-slate-300" />
                <span className="text-xs">{drawMode === "wall" ? "Đang vẽ..." : "Vẽ tường"}</span>
              </Button>

              <Button
                variant="outline"
                className="flex flex-col items-center h-20 col-span-2 justify-center gap-2 border-dashed border-2"
                onClick={addZone}
                disabled={!editMode}
              >
                <Square className="w-6 h-6 text-blue-500" />
                <span className="text-xs">Khu vực (VIP, Ngoài trời)</span>
              </Button>
            </div>
          </div>

          {/* Properties Panel */}
          {selectedId && editMode && (
            <div className="space-y-4 pt-4 border-t">
              <h3 className="text-xs font-semibold uppercase text-muted-foreground tracking-wider">
                Thuộc Tính Chọn ({currentFloor.objects.find(o => o.id === selectedId)?.type})
              </h3>
              
              {currentFloor.objects.find(o => o.id === selectedId)?.type === "table" && (
                <div className="space-y-2">
                  <label className="text-sm font-medium">Trạng thái Demo</label>
                  <Select 
                    value={currentFloor.objects.find(o => o.id === selectedId)?.status}
                    onValueChange={(val) => {
                      updateObjects(prev => prev.map(o => o.id === selectedId ? { ...o, status: val } : o));
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Chọn màu" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="available">Trống (Xanh)</SelectItem>
                      <SelectItem value="occupied">Đang phục vụ (Đỏ)</SelectItem>
                      <SelectItem value="reserved">Đã đặt (Vàng)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              )}

              <Button variant="destructive" className="w-full" onClick={deleteSelected}>
                Xóa đối tượng (Del)
              </Button>

            </div>
          )}
        </div>
      </div>
      )}

      {/* Main Content */}
      <div className="flex-1 flex flex-col bg-slate-50 relative">
        {/* Top Navbar */}
        <div className="h-14 border-b bg-white flex items-center justify-between px-4 z-10 shadow-sm">
          <div className="flex items-center gap-4">
            <Select value={currentFloorId} onValueChange={(val) => { setCurrentFloorId(val); setSelectedId(null); setDrawMode("none"); }}>
              <SelectTrigger className="w-48 font-semibold bg-primary/5 border-primary/20">
                <SelectValue placeholder="Chọn Tầng" />
              </SelectTrigger>
              <SelectContent>
                {floors.map((f) => (
                  <SelectItem key={f.id} value={f.id}>{f.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            {mode === "edit" && (
            <Button variant="ghost" size="sm" onClick={() => setFloors([...floors, { id: `floor-${Date.now()}`, name: `Tầng ${floors.length + 1}`, objects: [] }])}>
              <Plus className="w-4 h-4 mr-1" /> Thêm Tầng
            </Button>
            )}
          </div>

          {mode === "edit" && (
          <div className="flex items-center gap-2">
            <Button
              variant={editMode ? "outline" : "default"}
              size="sm"
              onClick={() => { setEditMode(!editMode); setSelectedId(null); setDrawMode("none"); }}
            >
              {editMode ? <Eye className="w-4 h-4 mr-2" /> : <EyeOff className="w-4 h-4 mr-2" />}
              {editMode ? "Chuyển sang Chế độ Xem" : "Bật Chế độ Sửa"}
            </Button>
            {editMode && (
              <Button size="sm" onClick={handleSave} className="bg-emerald-600 hover:bg-emerald-700 text-white">
                <Save className="w-4 h-4 mr-2" />
                Áp Dụng (Apply)
              </Button>
            )}
          </div>
          )}
        </div>

        {/* Canvas Area */}
        <div className="flex-1 relative cursor-crosshair min-w-0 min-h-0 overflow-hidden">
          <FloorPlanCanvas 
             objects={currentFloor.objects} 
             setObjects={(newObjects) => updateObjects(newObjects)} 
             editMode={editMode}
             selectedId={selectedId}
             setSelectedId={(id) => {
               setSelectedId(id);
               if (mode === "view" && id && onTableClick) {
                 const obj = currentFloor.objects.find(o => o.id === id);
                 if (obj && obj.type === "table") {
                   onTableClick(obj.name || obj.id); 
                 }
               }
             }}
             drawMode={drawMode}
             onWallFinish={() => setDrawMode("none")}
          />
        </div>
      </div>
    </div>
  );
}
