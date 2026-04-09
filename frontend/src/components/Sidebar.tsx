"use client";

import { cn } from "@/lib/utils";
import { MessageSquare, Plus, X, Database, CalendarClock } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { UploadModal } from "./UploadModal";
import { StudyPlanModal } from "./StudyPlanModal";

interface Session {
  memoryId: number;
  title: string;
}

interface SessionPageResponse {
  items: Session[];
  page: number;
  size: number;
  hasNext: boolean;
}

const SESSION_PAGE_SIZE = 20;

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  currentSessionId: number;
  onSelectSession: (id: number) => void;
  refreshKey?: number;
}

export function Sidebar({ isOpen, onClose, currentSessionId, onSelectSession, refreshKey = 0 }: SidebarProps) {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [page, setPage] = useState(1);
  const [hasNext, setHasNext] = useState(false);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [isStudyPlanOpen, setIsStudyPlanOpen] = useState(false);

  const loadSessions = useCallback((targetPage: number, append: boolean) => {
    return fetch(`/api/agent/history/sessions?page=${targetPage}&size=${SESSION_PAGE_SIZE}`)
      .then(res => res.json())
      .then((data: SessionPageResponse | Session[]) => {
        if (Array.isArray(data)) {
          setSessions(data);
          setPage(1);
          setHasNext(false);
          return;
        }
        const items = Array.isArray(data?.items) ? data.items : [];
        setSessions(prev => (append ? [...prev, ...items] : items));
        setPage(data?.page ?? targetPage);
        setHasNext(Boolean(data?.hasNext));
      })
      .catch(err => console.error("Failed to fetch sessions", err));
  }, []);

  useEffect(() => {
    loadSessions(1, false);
  }, [loadSessions, refreshKey]);

  const handleLoadMore = () => {
    setIsLoadingSessions(true);
    loadSessions(page + 1, true)
      .finally(() => setIsLoadingSessions(false));
  };

  return (
    <>
      <aside
        className={cn(
          "fixed md:static top-0 left-0 h-full bg-[#eef0f2] w-72 flex flex-col z-40 transition-transform duration-300 ease-in-out border-r border-slate-200/60",
          isOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"
        )}
      >
        <div className="p-4 flex items-center justify-between gap-2">
          <button 
            onClick={() => onSelectSession(Date.now())}
            className="flex items-center gap-2 flex-1 bg-white hover:bg-slate-50 border border-slate-200 transition-colors p-3 rounded-2xl text-sm font-medium shadow-sm"
          >
            <Plus size={18} />
            New Chat
          </button>
          
          <button 
            onClick={() => setIsUploadModalOpen(true)}
            title="上传知识库"
            className="flex items-center justify-center bg-white hover:bg-slate-50 border border-slate-200 transition-colors p-3 rounded-2xl text-slate-600 shadow-sm"
          >
            <Database size={18} />
          </button>

          <button
            onClick={() => setIsStudyPlanOpen(true)}
            title="学习计划提醒"
            className="flex items-center justify-center bg-white hover:bg-slate-50 border border-slate-200 transition-colors p-3 rounded-2xl text-slate-600 shadow-sm"
          >
            <CalendarClock size={18} />
          </button>
          
          <button onClick={onClose} className="md:hidden p-2 rounded-2xl hover:bg-slate-200">
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
          {hasNext && (
            <button
              onClick={handleLoadMore}
              disabled={isLoadingSessions}
              className="w-full mt-2 p-2 rounded-2xl border border-slate-200 bg-white hover:bg-slate-50 text-xs text-slate-600 disabled:opacity-60"
            >
              {isLoadingSessions ? "加载中..." : "加载更多"}
            </button>
          )}
        </div>
      </aside>

      <UploadModal 
        isOpen={isUploadModalOpen} 
        onClose={() => setIsUploadModalOpen(false)} 
      />

      <StudyPlanModal
        isOpen={isStudyPlanOpen}
        onClose={() => setIsStudyPlanOpen(false)}
      />
    </>
  );
}
