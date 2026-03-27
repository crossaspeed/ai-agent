"use client";

import { useState, useEffect } from "react";
import { ChatInput } from "./ChatInput";
import { MessageBubble } from "./MessageBubble";
import { motion } from "framer-motion";

export interface Message {
  id: string;
  role: "user" | "ai";
  content: string;
}

export function ChatArea({ 
  currentSessionId, 
  onChatUpdate 
}: { 
  currentSessionId: number,
  onChatUpdate?: () => void
}) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  // 组件挂载或切换 session 时获取历史记录
  useEffect(() => {
    setMessages([]);
    fetch(`/api/agent/history/${currentSessionId}`)
      .then((res) => {
        if (!res.ok) throw new Error("Failed to fetch history");
        return res.json();
      })
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          const historyMsgs: Message[] = data.map((m: any, idx: number) => ({
            id: `hist-${idx}`,
            role: m.type === "USER" ? "user" : "ai",
            content: m.text || m.content || "", 
          }));
          setMessages(historyMsgs);
        } else if (data.length === 0) {
          setMessages([
            {
              id: "welcome",
              role: "ai",
              content: "你好！我是你的 AI 助手。我们已经开始了新的对话，有什么可以帮你的吗？",
            },
          ]);
        }
      })
      .catch((err) => {
        console.error("Failed to load history:", err);
      });
  }, [currentSessionId]);

  const handleSendMessage = async (content: string) => {
    if (isLoading || !content.trim()) return;

    const newUserMsg: Message = { id: Date.now().toString(), role: "user", content };
    setMessages((prev) => [...prev, newUserMsg]);
    setIsLoading(true);

    const aiMsgId = (Date.now() + 1).toString();
    setMessages((prev) => [...prev, { id: aiMsgId, role: "ai", content: "" }]);

    try {
      const response = await fetch('/api/agent/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ memoryId: currentSessionId, message: content }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error("No reader available");
      const decoder = new TextDecoder("utf-8");
      
      let aiText = "";
      let done = false;

      while (!done) {
        const { value, done: readerDone } = await reader.read();
        done = readerDone;
        if (value) {
          const chunk = decoder.decode(value, { stream: true });
          
          let textToAdd = chunk;
          if (textToAdd.includes('data:')) {
             textToAdd = textToAdd
               .split('\n')
               .filter(line => line.startsWith('data:'))
               .map(line => line.replace('data:', ''))
               .join('');
          }

          aiText += textToAdd;
          
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMsgId ? { ...msg, content: aiText } : msg
            )
          );
        }
      }
      
      // 当消息完整接收后，触发更新以刷新侧边栏的历史记录列表
      if (onChatUpdate) {
        onChatUpdate();
      }

    } catch (error) {
      console.error("Chat error:", error);
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === aiMsgId ? { ...msg, content: "⚠️ 通讯失败，请检查后端是否正常运行。" } : msg
        )
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-full w-full relative">
      <div className="flex-1 overflow-y-auto px-4 py-6 md:px-8 pb-32 scroll-smooth">
        {messages.map((msg, index) => (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
            key={msg.id}
          >
            <MessageBubble message={msg} />
          </motion.div>
        ))}
      </div>
      
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-[#f8f9fa] via-[#f8f9fa] to-transparent pt-10 pb-6 px-4 md:px-8">
        <ChatInput onSend={handleSendMessage} />
        <div className="text-center text-[11px] text-slate-400 mt-3">
          AI generated content may be inaccurate.
        </div>
      </div>
    </div>
  );
}
