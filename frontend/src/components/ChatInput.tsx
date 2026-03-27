"use client";

import { useState, useRef, useEffect } from "react";
import { ArrowUp } from "lucide-react";
import { cn } from "@/lib/utils";

interface ChatInputProps {
  onSend: (message: string) => void;
}

export function ChatInput({ onSend }: ChatInputProps) {
  const [input, setInput] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 自动调整高度
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [input]);

  const handleSend = () => {
    if (input.trim()) {
      onSend(input.trim());
      setInput("");
      if (textareaRef.current) {
        textareaRef.current.style.height = "auto";
      }
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="relative flex items-end w-full max-w-3xl mx-auto bg-white border border-slate-200 rounded-3xl shadow-sm focus-within:ring-2 focus-within:ring-slate-200 focus-within:border-slate-300 transition-all">
      <textarea
        ref={textareaRef}
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Ask anything..."
        className="w-full max-h-[200px] bg-transparent py-4 pl-5 pr-14 outline-none resize-none placeholder:text-slate-400 text-slate-700 leading-relaxed scrollbar-thin rounded-3xl"
        rows={1}
      />
      <div className="absolute right-2 bottom-2">
        <button
          onClick={handleSend}
          disabled={!input.trim()}
          className={cn(
            "p-2 rounded-2xl transition-all flex items-center justify-center h-10 w-10",
            input.trim() 
              ? "bg-slate-800 text-white hover:bg-slate-700 shadow-md" 
              : "bg-slate-100 text-slate-400"
          )}
        >
          <ArrowUp size={20} strokeWidth={2.5} />
        </button>
      </div>
    </div>
  );
}
