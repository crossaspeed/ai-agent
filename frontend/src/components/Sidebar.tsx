"use client";

import { cn } from "@/lib/utils";
import { MessageSquare, Plus, X } from "lucide-react";
import { useEffect, useState } from "react";

interface Session {
  memoryId: number;
  title: string;
}

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  currentSessionId: number;
  onSelectSession: (id: number) => void;
}

export function Sidebar({ isOpen, onClose, currentSessionId, onSelectSession }: SidebarProps) {
  const [sessions, setSessions] = useState<Session[]>([]);

  useEffect(() => {
    fetch('/api/agent/history/sessions')
      .then(res => res.json())
      .then(data => {
        if (Array.isArray(data)) {
          setSessions(data);
        }
      })
      .catch(err => console.error("Failed to fetch sessions", err));
  }, []);

  return (
    <aside
      className={cn(
        "fixed md:static top-0 left-0 h-full bg-[#eef0f2] w-72 flex flex-col z-40 transition-transform duration-300 ease-in-out border-r border-slate-200/60",
        isOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"
      )}
    >
      <div className="p-4 flex items-center justify-between">
        <button 
          onClick={() => onSelectSession(Date.now())}
          className="flex items-center gap-2 w-full bg-white hover:bg-slate-50 border border-slate-200 transition-colors p-3 rounded-2xl text-sm font-medium shadow-sm"
        >
          <Plus size={18} />
          New Chat
        </button>
        <button onClick={onClose} className="md:hidden ml-2 p-2 rounded-2xl hover:bg-slate-200">
          <X size={20} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-2 space-y-1">
        <div className="text-xs font-semibold text-slate-500 mb-3 px-2">Recent</div>
        {sessions.map((session) => (
          <button
            key={session.memoryId}
            onClick={() => onSelectSession(session.memoryId)}
            className={cn(
              "flex items-center gap-3 w-full text-left p-3 rounded-2xl transition-colors text-sm text-slate-700",
              currentSessionId === session.memoryId ? "bg-slate-200/80 font-medium" : "hover:bg-slate-200/50"
            )}
          >
            <MessageSquare size={16} className="text-slate-400 shrink-0" />
            <span className="truncate">{session.title}</span>
          </button>
        ))}
      </div>
    </aside>
  );
}
