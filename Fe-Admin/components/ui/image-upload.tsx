"use client";

import { useRef, useState, useCallback } from "react";
import { ImageIcon, Upload, Loader2 } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/axios";
import { cn } from "@/lib/utils";

interface ImageUploadProps {
    value: string;
    onChange: (url: string) => void;
    className?: string;
}

export function ImageUpload({ value, onChange, className }: ImageUploadProps) {
    const inputRef = useRef<HTMLInputElement>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [isUploading, setIsUploading] = useState(false);

    const upload = useCallback(async (file: File) => {
        if (!file.type.startsWith("image/")) {
            toast.error("Chỉ chấp nhận file ảnh (jpg, png, webp...)");
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            toast.error("File ảnh không được vượt quá 5MB");
            return;
        }

        setIsUploading(true);
        try {
            const formData = new FormData();
            formData.append("image", file);
            const res = await api.post("/images/upload/foods", formData, {
                headers: { "Content-Type": "multipart/form-data" },
            });
            onChange(res.data.url);
            toast.success("Tải ảnh thành công");
        } catch {
            toast.error("Tải ảnh thất bại, vui lòng thử lại");
        } finally {
            setIsUploading(false);
        }
    }, [onChange]);

    const handleDrop = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(false);
        const file = e.dataTransfer.files[0];
        if (file) upload(file);
    }, [upload]);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) upload(file);
        e.target.value = "";
    };

    const handleRemove = (e: React.MouseEvent) => {
        e.stopPropagation();
        onChange("");
    };

    return (
        <div className={cn("relative", className)}>
            <input
                ref={inputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleFileChange}
            />

            {value ? (
                // Preview
                <div className="relative rounded-lg overflow-hidden border border-border aspect-video w-full bg-muted">
                    <img
                        src={value.startsWith("/api/") ? `${window.location.protocol}//${window.location.hostname}:3000${value}` : value}
                        alt="Ảnh món ăn"
                        className="w-full h-full object-cover"
                    />
                    {/* Buttons always visible, bottom-right corner */}
                    <div className="absolute bottom-2 right-2 flex items-center gap-1.5">
                        <button
                            type="button"
                            onClick={() => inputRef.current?.click()}
                            className="px-3 py-1.5 text-xs bg-white text-black rounded-md font-medium shadow hover:bg-gray-100"
                        >
                            Thay ảnh
                        </button>
                        <button
                            type="button"
                            onClick={handleRemove}
                            className="p-1.5 bg-red-600 text-white rounded-md shadow hover:bg-red-700 flex items-center justify-center"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M18 6L6 18"/><path d="M6 6l12 12"/>
                            </svg>
                        </button>
                    </div>
                </div>
            ) : (
                // Drop zone
                <div
                    role="button"
                    tabIndex={0}
                    onClick={() => !isUploading && inputRef.current?.click()}
                    onKeyDown={(e) => e.key === "Enter" && !isUploading && inputRef.current?.click()}
                    onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
                    onDragLeave={() => setIsDragging(false)}
                    onDrop={handleDrop}
                    className={cn(
                        "flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed cursor-pointer transition-colors aspect-video w-full",
                        isDragging
                            ? "border-primary bg-primary/5"
                            : "border-border bg-muted/40 hover:border-primary/60 hover:bg-muted/70",
                        isUploading && "pointer-events-none opacity-60"
                    )}
                >
                    {isUploading ? (
                        <>
                            <Loader2 className="h-8 w-8 text-primary animate-spin" />
                            <p className="text-sm text-muted-foreground">Đang tải lên...</p>
                        </>
                    ) : (
                        <>
                            {isDragging ? (
                                <Upload className="h-8 w-8 text-primary" />
                            ) : (
                                <ImageIcon className="h-8 w-8 text-muted-foreground" />
                            )}
                            <div className="text-center">
                                <p className="text-sm font-medium">
                                    {isDragging ? "Thả ảnh vào đây" : "Kéo thả hoặc nhấn để chọn ảnh"}
                                </p>
                                <p className="text-xs text-muted-foreground mt-0.5">JPG, PNG, WebP · Tối đa 5MB</p>
                            </div>
                        </>
                    )}
                </div>
            )}
        </div>
    );
}
