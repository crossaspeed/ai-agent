"use client";

import { useState, useRef, useEffect } from "react";
import { ArrowUp } from "lucide-react";
import { cn } from "@/lib/utils";

interface ChatInputProps {
  onSend: (payload: { message: string; type?: string }) => void;
}

interface RouteCommandOption {
  type: string;
  command: string;
  title: string;
  description: string;
}

const ROUTE_COMMAND_OPTIONS: RouteCommandOption[] = [
  {
    type: "help",
    command: "/help",
    title: "帮助",
    description: "查看可用功能与示例命令",
  },
  {
    type: "plan",
    command: "/plan",
    title: "学习计划",
    description: "创建或调整学习计划模式",
  },
  {
    type: "qa",
    command: "/qa",
    title: "知识问答",
    description: "进入知识库问答模式",
  },
];

function parseSlashCommand(input: string): { message: string; type?: string } {
  const trimmed = input.trim();
  const match = trimmed.match(/^\/([a-zA-Z]+)\b/);
  if (!match) {
    return { message: trimmed };
  }

  const command = `/${match[1].toLowerCase()}`;
  const option = ROUTE_COMMAND_OPTIONS.find((item) => item.command === command);
  if (!option) {
    return { message: trimmed };
  }

  const message = trimmed.substring(match[0].length).trim();
  return { message, type: option.type };
}

export function ChatInput({ onSend }: ChatInputProps) {
  const [input, setInput] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const slashQuery = input.trimStart().startsWith("/")
    ? input.trimStart().slice(1).toLowerCase()
    : "";
  const showCommandPanel = input.trimStart().startsWith("/");
  const visibleCommandOptions = ROUTE_COMMAND_OPTIONS.filter((option) =>
    slashQuery.length === 0 ? true : option.command.slice(1).startsWith(slashQuery)
  );

  // 自动调整高度
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [input]);

  const handleSend = () => {
    const payload = parseSlashCommand(input);
    if (payload.message || payload.type) {
      onSend(payload);
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
    <div className="relative w-full max-w-3xl mx-auto">
      {showCommandPanel && (
        <div className="absolute left-0 right-0 bottom-[calc(100%+10px)] bg-white border border-slate-200 rounded-2xl shadow-lg p-2 z-20">
          {visibleCommandOptions.length > 0 ? (
            visibleCommandOptions.map((option) => (
              <button
                key={option.type}
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  setInput(`${option.command} `);
                  textareaRef.current?.focus();
                }}
                className="w-full text-left px-3 py-2 rounded-xl hover:bg-slate-50 transition-colors"
              >
                <div className="text-sm font-medium text-slate-800">{option.command} · {option.title}</div>
                <div className="text-xs text-slate-500 mt-0.5">{option.description}</div>
              </button>
            ))
          ) : (
            <div className="px-3 py-2 text-sm text-slate-500">未找到匹配命令，可用：/help /plan /qa</div>
          )}
        </div>
      )}

      <div className="relative flex items-end w-full bg-white border border-slate-200 rounded-3xl shadow-sm focus-within:ring-2 focus-within:ring-slate-200 focus-within:border-slate-300 transition-all">
      <textarea
        ref={textareaRef}
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="输入问题，或输入 / 查看模式（/help /plan /qa）"
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
    </div>
  );
}
