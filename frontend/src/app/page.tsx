"use client";

import { useState } from "react";
import { Sidebar } from "@/components/Sidebar";
import { ChatArea } from "@/components/ChatArea";
import { Menu } from "lucide-react";

export default function Home() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<number>(1);
  const [refreshKey, setRefreshKey] = useState<number>(0);

  return (
    <div className="flex h-screen w-full bg-[#f8f9fa] overflow-hidden text-slate-800">
      <div className="md:hidden fixed top-0 left-0 right-0 h-14 bg-white/80 backdrop-blur-md z-20 flex items-center px-4 border-b border-slate-200">
        <button 
          onClick={() => setIsSidebarOpen(true)}
          className="p-2 -ml-2 rounded-2xl hover:bg-slate-100 text-slate-600"
        >
          <Menu size={24} />
        </button>
        <span className="ml-2 font-medium">New Chat</span>
      </div>

      <Sidebar 
        isOpen={isSidebarOpen} 
        onClose={() => setIsSidebarOpen(false)} 
        currentSessionId={currentSessionId}
        onSelectSession={(id) => {
          setCurrentSessionId(id);
          setIsSidebarOpen(false);
        }}
        refreshKey={refreshKey}
      />

      {isSidebarOpen && (
        <div 
          className="fixed inset-0 bg-black/20 z-30 md:hidden"
          onClick={() => setIsSidebarOpen(false)}
        />
      )}

      <main className="flex-1 flex flex-col h-full relative max-w-4xl mx-auto w-full pt-14 md:pt-0">
        <ChatArea 
          currentSessionId={currentSessionId} 
          onChatUpdate={() => setRefreshKey(prev => prev + 1)} 
        />
      </main>
    </div>
  );
}
