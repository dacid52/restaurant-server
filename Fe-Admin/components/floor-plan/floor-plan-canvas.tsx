"use client";

import React, { useRef, useEffect, useState } from "react";
import { Stage, Layer, Rect, Text, Line, Group, Transformer } from "react-konva";
import Konva from "konva";

export interface PlanObject {
  id: string;
  type: "table" | "wall" | "zone";
  x: number;
  y: number;
  width?: number;
  height?: number;
  rotation?: number;
  points?: number[];
  name?: string;
  status?: "available" | "occupied" | "reserved" | string;
  color?: string;
}

interface CanvasProps {
  objects: PlanObject[];
  setObjects: React.Dispatch<React.SetStateAction<PlanObject[]>>;
  editMode: boolean;
  selectedId: string | null;
  setSelectedId: (id: string | null) => void;
  drawMode: "none" | "wall";
  onWallFinish: () => void;
}

const GRID_SIZE = 20;

export default function FloorPlanCanvas({
  objects,
  setObjects,
  editMode,
  selectedId,
  setSelectedId,
  drawMode,
  onWallFinish,
}: CanvasProps) {
  const stageRef = useRef<Konva.Stage>(null);
  const layerRef = useRef<Konva.Layer>(null);
  const trRef = useRef<Konva.Transformer>(null);

  const [scale, setScale] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [newWallPoints, setNewWallPoints] = useState<number[]>([]);
  const wallPointsRef = useRef<number[]>([]);
  const [isDrawingWall, setIsDrawingWall] = useState(false);
  const [hoverId, setHoverId] = useState<string | null>(null);

  // Attach transformer
  useEffect(() => {
    if (editMode && selectedId && trRef.current && layerRef.current) {
      const node = layerRef.current.findOne(`#${selectedId}`);
      if (node) {
        trRef.current.nodes([node]);
        trRef.current.getLayer()?.batchDraw();
      } else {
        trRef.current.nodes([]);
      }
    } else if (trRef.current) {
      trRef.current.nodes([]);
    }
  }, [selectedId, editMode, objects]);

  const snapToGrid = (val: number) => {
    return Math.round(val / GRID_SIZE) * GRID_SIZE;
  };

  const checkDeselect = (e: Konva.KonvaEventObject<MouseEvent | TouchEvent>) => {
    const clickedOnEmpty = e.target === e.target.getStage();
    if (clickedOnEmpty) {
      setSelectedId(null);
    }
  };

  const handleStageClick = (e: Konva.KonvaEventObject<MouseEvent | TouchEvent>) => {
    if (!editMode) return;

    // Wall drawing logic
    if (drawMode === "wall") {
      const stage = e.target.getStage();
      if (!stage) return;
      const pointerPos = stage.getRelativePointerPosition();
      if (!pointerPos) return;

      const x = snapToGrid(pointerPos.x);
      const y = snapToGrid(pointerPos.y);

      if (!isDrawingWall) {
        setIsDrawingWall(true);
        const pts = [x, y];
        wallPointsRef.current = pts;
        setNewWallPoints(pts);
      } else {
        const pts = [...wallPointsRef.current, x, y];
        wallPointsRef.current = pts;
        setNewWallPoints(pts);
      }
      return;
    }

    checkDeselect(e);
  };

  const handleStageMouseMove = (e: Konva.KonvaEventObject<MouseEvent | TouchEvent>) => {
    if (drawMode === "wall" && isDrawingWall) {
      const stage = e.target.getStage();
      if (!stage) return;
      const pointerPos = stage.getRelativePointerPosition();
      if (!pointerPos) return;

      const x = snapToGrid(pointerPos.x);
      const y = snapToGrid(pointerPos.y);

      const points = [...wallPointsRef.current];
      // update last phantom point
      if (points.length >= 2) {
        // If we are moving, we replace the last two coords (which act as preview) 
        // Or we keep appending? Usually we add a preview point.
      }
    }
  };

  const handleStageDblClick = (e: Konva.KonvaEventObject<MouseEvent>) => {
    if (drawMode === "wall" && isDrawingWall) {
      setIsDrawingWall(false);
      const pointsFinal = [...wallPointsRef.current];
      if (pointsFinal.length >= 4) {
        const newWall: PlanObject = {
          id: `wall-${Date.now()}`,
          type: "wall",
          points: pointsFinal,
          x: 0,
          y: 0,
        };
        setObjects((prev) => [...prev, newWall]);
      }
      setNewWallPoints([]);
      wallPointsRef.current = [];
      onWallFinish();
    }
  };

  const handleDragEnd = (e: Konva.KonvaEventObject<DragEvent>, id: string) => {
    const node = e.target;
    const x = snapToGrid(node.x());
    const y = snapToGrid(node.y());

    // Snapping back visually
    node.position({ x, y });

    setObjects(
      objects.map((obj) =>
        obj.id === id ? { ...obj, x, y } : obj
      )
    );
  };

  const handleTransformEnd = (e: Konva.KonvaEventObject<Event>, id: string) => {
    const node = e.target;
    const scaleX = node.scaleX();
    const scaleY = node.scaleY();

    node.scaleX(1);
    node.scaleY(1);

    const oldObj = objects.find((o) => o.id === id);
    if (!oldObj) return;

    const newWidth = Math.max(5, (oldObj.width || 0) * scaleX);
    const newHeight = Math.max(5, (oldObj.height || 0) * scaleY);

    setObjects(
      objects.map((obj) =>
        obj.id === id
          ? {
              ...obj,
              x: node.x(),
              y: node.y(),
              rotation: node.rotation(),
              width: newWidth,
              height: newHeight,
            }
          : obj
      )
    );
  };

  const handleWheel = (e: Konva.KonvaEventObject<WheelEvent>) => {
    e.evt.preventDefault();
    if (!editMode) return; // Khóa zoom in/out khi ở chế độ xem
    
    const scaleBy = 1.1;
    const stage = stageRef.current;
    if (!stage) return;

    const oldScale = stage.scaleX();
    const pointer = stage.getPointerPosition();
    if (!pointer) return;

    const mousePointTo = {
      x: (pointer.x - stage.x()) / oldScale,
      y: (pointer.y - stage.y()) / oldScale,
    };

    const newScale = e.evt.deltaY < 0 ? oldScale * scaleBy : oldScale / scaleBy;
    setScale(newScale);

    setPosition({
      x: pointer.x - mousePointTo.x * newScale,
      y: pointer.y - mousePointTo.y * newScale,
    });
  };

  const getTableColor = (status?: string) => {
    switch (status) {
      case "available":
        return "#10B981"; // green
      case "occupied":
        return "#EF4444"; // red
      case "reserved":
        return "#F59E0B"; // yellow
      default:
        return "#6B7280";
    }
  };

  const containerRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 800, height: 600 });

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setSize({ width: entry.contentRect.width, height: entry.contentRect.height });
      }
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={containerRef} className="w-full h-full bg-slate-50 relative overflow-hidden min-w-0 min-h-0">
      {/* Grid Pattern Background */}
      {editMode && (
        <div 
          className="absolute inset-0 pointer-events-none opacity-20"
          style={{
            backgroundImage: `linear-gradient(#cbd5e1 1px, transparent 1px), linear-gradient(90deg, #cbd5e1 1px, transparent 1px)`,
            backgroundSize: `${GRID_SIZE * scale}px ${GRID_SIZE * scale}px`,
            backgroundPosition: `${position.x}px ${position.y}px`
          }}
        />
      )}
      
      <div className="absolute inset-0 pointer-events-auto">
        <Stage
          ref={stageRef}
          width={size.width}
          height={size.height}
        draggable={editMode && drawMode === "none"} // Khóa pan khi ở chế độ xem 
        onWheel={handleWheel}
        onClick={handleStageClick}
        onDblClick={handleStageDblClick}
        onMouseMove={handleStageMouseMove}
        scaleX={scale}
        scaleY={scale}
        x={position.x}
        y={position.y}
        style={{ cursor: drawMode === "wall" ? "crosshair" : (editMode ? "default" : "grab") }}
      >
        <Layer ref={layerRef}>
          {objects.map((obj) => {
            const isHovered = hoverId === obj.id;
            const isSelected = selectedId === obj.id;

            if (obj.type === "zone") {
              return (
                <Group
                  key={obj.id}
                  id={obj.id}
                  x={obj.x}
                  y={obj.y}
                  width={obj.width}
                  height={obj.height}
                  rotation={obj.rotation || 0}
                  draggable={editMode && drawMode === "none"}
                  onClick={() => setSelectedId(obj.id)}
                  onDragEnd={(e) => handleDragEnd(e, obj.id)}
                  onTransformEnd={(e) => handleTransformEnd(e, obj.id)}
                  onMouseEnter={() => editMode && setHoverId(obj.id)}
                  onMouseLeave={() => setHoverId(null)}
                >
                  <Rect
                    width={obj.width}
                    height={obj.height}
                    fill={obj.color || "rgba(59, 130, 246, 0.2)"}
                    stroke={isSelected ? "#2563EB" : (isHovered ? "#93C5FD" : "transparent")}
                    strokeWidth={isSelected ? 2 : 1}
                    dash={[5, 5]}
                    cornerRadius={8}
                  />
                  <Text
                    text={obj.name || "Zone"}
                    fontSize={20}
                    fill="#1E40AF"
                    width={obj.width}
                    height={obj.height}
                    align="center"
                    verticalAlign="middle"
                    fontStyle="bold"
                  />
                </Group>
              );
            }

            if (obj.type === "table") {
              return (
                <Group
                  key={obj.id}
                  id={obj.id}
                  x={obj.x}
                  y={obj.y}
                  width={obj.width}
                  height={obj.height}
                  rotation={obj.rotation || 0}
                  draggable={editMode && drawMode === "none"}
                  onClick={() => setSelectedId(obj.id)}
                  onDragEnd={(e) => handleDragEnd(e, obj.id)}
                  onTransformEnd={(e) => handleTransformEnd(e, obj.id)}
                  onMouseEnter={() => editMode && setHoverId(obj.id)}
                  onMouseLeave={() => setHoverId(null)}
                >
                  <Rect
                    width={obj.width}
                    height={obj.height}
                    fill={getTableColor(obj.status)}
                    cornerRadius={10}
                    shadowColor="black"
                    shadowBlur={10}
                    shadowOpacity={isSelected ? 0.4 : 0.2}
                    shadowOffset={{ x: 2, y: 4 }}
                    stroke={isSelected ? "#000" : (isHovered ? "#fff" : undefined)}
                    strokeWidth={isSelected ? 2 : (isHovered ? 2 : 0)}
                  />
                  <Text
                    text={obj.name || "Table"}
                    fontSize={14}
                    fill="#fff"
                    width={obj.width}
                    height={obj.height}
                    align="center"
                    verticalAlign="middle"
                    fontStyle="bold"
                  />
                </Group>
              );
            }

            if (obj.type === "wall") {
              return (
                <Line
                  key={obj.id}
                  id={obj.id}
                  x={obj.x}
                  y={obj.y}
                  points={obj.points || []}
                  stroke={isSelected ? "#2563EB" : (isHovered ? "#64748B" : "#334155")}
                  strokeWidth={obj.width || 6}
                  lineCap="round"
                  lineJoin="round"
                  draggable={editMode && drawMode === "none"}
                  onClick={() => setSelectedId(obj.id)}
                  onDragEnd={(e) => handleDragEnd(e, obj.id)}
                  onMouseEnter={() => editMode && setHoverId(obj.id)}
                  onMouseLeave={() => setHoverId(null)}
                />
              );
            }
            return null;
          })}

          {/* Drawing Wall Preview */}
          {isDrawingWall && newWallPoints.length > 0 && (
            <Line
              points={newWallPoints}
              stroke="#334155"
              strokeWidth={6}
              lineCap="round"
              lineJoin="round"
              dash={[10, 5]}
            />
          )}

          {/* Transformer */}
          {editMode && <Transformer ref={trRef} boundBoxFunc={(oldBox, newBox) => newBox} />}
        </Layer>
        </Stage>
      </div>
    </div>
  );
}
