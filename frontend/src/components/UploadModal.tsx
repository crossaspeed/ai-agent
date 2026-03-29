"use client";

import { useState } from "react";
import { X, UploadCloud, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function UploadModal({ isOpen, onClose }: UploadModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [topic, setTopic] = useState("");
  const [isUploading, setIsUploading] = useState(false);
  const [message, setMessage] = useState<{ text: string; type: "success" | "error" } | null>(null);

  if (!isOpen) return null;

  const handleUpload = async () => {
    if (!file || !topic.trim()) return;

    setIsUploading(true);
    setMessage(null);

    const formData = new FormData();
    formData.append("file", file);
    formData.append("topic", topic.trim());

    try {
      const res = await fetch("/api/knowledge/upload", {
        method: "POST",
        body: formData,
      });
      const data = await res.json();
      
      if (data.status === "success") {
        setMessage({ text: data.message || "上传并向量化成功！", type: "success" });
        setFile(null);
        setTopic("");
      } else {
        setMessage({ text: data.message || "上传失败", type: "error" });
      }
    } catch {
      setMessage({ text: "网络或服务器错误，请重试", type: "error" });
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm px-4">
      <div className="bg-white rounded-3xl w-full max-w-md p-6 shadow-xl relative animate-in fade-in zoom-in duration-200">
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 p-2 rounded-full hover:bg-slate-100 text-slate-500 transition-colors"
        >
          <X size={20} />
        </button>

        <h2 className="text-xl font-semibold mb-6 flex items-center gap-2">
          <UploadCloud className="text-blue-500" />
          知识库上传 (Pinecone)
        </h2>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">主题 (Topic)</label>
            <input 
              type="text" 
              placeholder="例如：计算机网络、MySQL"
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              className="w-full border border-slate-300 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all"
            />
            <p className="text-xs text-slate-500 mt-1.5 ml-1">
              设定明确的主题可以帮助 AI 在被问及此领域时，精准检索这些文件内容。
            </p>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">选择文件</label>
            <input 
              type="file" 
              accept=".txt,.md,.pdf,.docx"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
              className="w-full text-sm text-slate-500 file:mr-4 file:py-2.5 file:px-4 file:rounded-xl file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 transition-all"
            />
            <div className="mt-2 p-3 bg-slate-50 rounded-xl border border-slate-100 text-xs text-slate-600 leading-relaxed space-y-1">
              <p className="font-medium text-slate-700">📌 文档排版建议：</p>
              <p>为了让 AI 问答效果最好，建议您的文档：</p>
              <ul className="list-disc pl-4 space-y-0.5">
                <li>按概念分段，段落之间留<strong>空行 (双回车)</strong></li>
                <li>如果是问答格式，最好写成：<br/><span className="font-mono text-[10px] bg-white px-1 rounded border">Q: ...换行 A: ...</span></li>
                <li>后端默认切分策略：每 500 字一块，重叠 50 字。</li>
              </ul>
            </div>
          </div>

          {message && (
            <div className={cn(
              "p-3 rounded-xl text-sm",
              message.type === "success" ? "bg-green-50 text-green-700 border border-green-200" : "bg-red-50 text-red-700 border border-red-200"
            )}>
              {message.text}
            </div>
          )}

          <button
            onClick={handleUpload}
            disabled={!file || !topic.trim() || isUploading}
            className="w-full mt-4 bg-slate-800 text-white rounded-xl py-3 font-medium hover:bg-slate-700 disabled:bg-slate-300 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2"
          >
            {isUploading ? <Loader2 size={18} className="animate-spin" /> : <UploadCloud size={18} />}
            {isUploading ? "正在解析并向量化..." : "上传到向量数据库"}
          </button>
        </div>
      </div>
    </div>
  );
}
